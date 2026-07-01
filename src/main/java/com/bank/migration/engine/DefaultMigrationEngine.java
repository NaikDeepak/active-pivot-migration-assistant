package com.bank.migration.engine;

import com.bank.migration.ai.AiException;
import com.bank.migration.ai.AiProvider;
import com.bank.migration.ai.AiResponse;
import com.bank.migration.compiler.CompilationResult;
import com.bank.migration.compiler.CompilerError;
import com.bank.migration.compiler.CompilerRunner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.console.DiffPrinter;
import com.bank.migration.console.SessionState;
import com.bank.migration.fix.FixHistory;
import com.bank.migration.fix.FixRecord;
import com.bank.migration.knowledge.KnowledgeLoader;
import com.bank.migration.knowledge.KnowledgeMatch;
import com.bank.migration.prompt.MigrationPrompt;
import com.bank.migration.prompt.PromptBuilder;
import com.bank.migration.prompt.PromptContext;
import com.bank.migration.similarity.CandidateMatch;
import com.bank.migration.similarity.SimilaritySearch;
import com.bank.migration.source.SourceLoadException;
import com.bank.migration.source.SourceLoader;
import com.bank.migration.source.SourceSnippet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;

@Component
public class DefaultMigrationEngine implements MigrationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultMigrationEngine.class);

    private final SourceLoader sourceLoader;
    private final KnowledgeLoader knowledgeLoader;
    private final SimilaritySearch similaritySearch;
    private final FixHistory fixHistory;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final DiffApplier diffApplier;
    private final CompilerRunner compilerRunner;
    private final DiffPrinter diffPrinter;
    private final MigrationProperties props;

    public DefaultMigrationEngine(SourceLoader sourceLoader,
                                   KnowledgeLoader knowledgeLoader,
                                   SimilaritySearch similaritySearch,
                                   FixHistory fixHistory,
                                   PromptBuilder promptBuilder,
                                   AiProvider aiProvider,
                                   DiffApplier diffApplier,
                                   CompilerRunner compilerRunner,
                                   DiffPrinter diffPrinter,
                                   MigrationProperties props) {
        this.sourceLoader = sourceLoader;
        this.knowledgeLoader = knowledgeLoader;
        this.similaritySearch = similaritySearch;
        this.fixHistory = fixHistory;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
        this.diffApplier = diffApplier;
        this.compilerRunner = compilerRunner;
        this.diffPrinter = diffPrinter;
        this.props = props;
    }

    @Override
    public FixOutcome fixNextError(SessionState session, BufferedReader in, PrintStream out)
            throws IOException {

        CompilerError error = session.currentError().orElse(null);
        if (error == null) {
            return new FixOutcome.NoError();
        }

        // ── 1. Load source context ────────────────────────────────────────────
        out.println("[1/5] Loading source context...");
        SourceSnippet snippet;
        try {
            snippet = sourceLoader.load(error);
        } catch (SourceLoadException e) {
            log.warn("Cannot load source for {}: {}", error.signature(), e.getMessage());
            return new FixOutcome.ApplyFailed("Cannot load source: " + e.getMessage());
        }

        // ── 2. Gather knowledge and JAR candidates ────────────────────────────
        out.println("[2/5] Searching knowledge base and JAR index...");
        List<KnowledgeMatch> knowledge  = knowledgeLoader.findRelevant(error);
        List<CandidateMatch> candidates = similaritySearch.findCandidates(error);
        List<FixRecord>      history    = fixHistory.findSimilar(error);

        out.printf("      %d knowledge match(es), %d JAR candidate(s), %d similar fix(es)%n",
                knowledge.size(), candidates.size(), history.size());

        // ── 3. Build prompt and call AI ───────────────────────────────────────
        PromptContext context = new PromptContext(error, snippet, knowledge, candidates, history);
        MigrationPrompt prompt = promptBuilder.build(context);

        out.printf("[3/5] Calling AI provider '%s' (~%d tokens)...%n",
                aiProvider.providerName(), prompt.estimatedTokens());

        AiResponse response;
        try {
            response = aiProvider.complete(prompt);
        } catch (AiException e) {
            log.warn("AI call failed for {}: {}", error.signature(), e.getMessage());
            return new FixOutcome.ApplyFailed("AI call failed: " + e.getMessage());
        }

        log.info("AI response: {} input tokens, {} output tokens",
                response.inputTokensUsed(), response.outputTokensUsed());

        // ── 4. Handle NEED_CONTEXT ────────────────────────────────────────────
        if (response.needsContext()) {
            return new FixOutcome.NeedsContext(response.contextRequest());
        }

        String diff = response.diffPatch();
        if (diff == null || diff.isBlank()) {
            return new FixOutcome.ApplyFailed("AI returned an empty diff");
        }

        // ── 5. Show diff and ask for approval ─────────────────────────────────
        out.println("[4/5] Proposed fix:");
        out.println();
        diffPrinter.print(diff, out);
        out.println();
        out.print("Apply this fix? [y/N] ");
        out.flush();

        String answer = in.readLine();
        if (answer == null || !answer.strip().equalsIgnoreCase("y")) {
            return new FixOutcome.Rejected();
        }

        // ── 6. Apply diff ─────────────────────────────────────────────────────
        out.println("[5/5] Applying and recompiling...");
        ApplyResult applyResult = diffApplier.apply(snippet.sourceFile(), diff);
        if (!applyResult.success()) {
            return new FixOutcome.ApplyFailed("Diff apply failed: " + applyResult.message());
        }

        // ── 7. Recompile and record ───────────────────────────────────────────
        CompilationResult recompile = compilerRunner.compile(props.projectRoot());
        boolean succeeded = recompile.success();
        session.updateCompilation(recompile);

        String details = String.join("; ", error.details());
        FixRecord fixRecord = new FixRecord(
                error.signature(),
                error.sourceFile().toString(),
                error.message(),
                details,
                diff,
                Instant.now().toString(),
                succeeded
        );
        fixHistory.record(fixRecord);

        if (succeeded) {
            out.printf("Recompile SUCCESS — %d error(s) remaining.%n",
                    recompile.errors().size());
        } else {
            out.printf("Recompile FAILURE — %d error(s) remain. Fix recorded for future reference.%n",
                    recompile.errors().size());
        }

        return new FixOutcome.Success(fixRecord);
    }
}
