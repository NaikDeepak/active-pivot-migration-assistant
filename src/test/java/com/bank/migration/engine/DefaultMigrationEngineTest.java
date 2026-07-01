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
import com.bank.migration.prompt.MigrationPrompt;
import com.bank.migration.prompt.PromptBuilder;
import com.bank.migration.similarity.SimilaritySearch;
import com.bank.migration.source.NumberedLine;
import com.bank.migration.source.SourceLoadException;
import com.bank.migration.source.SourceLoader;
import com.bank.migration.source.SourceSnippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultMigrationEngineTest {

    @TempDir
    Path tempDir;

    private SourceLoader sourceLoader;
    private KnowledgeLoader knowledgeLoader;
    private SimilaritySearch similaritySearch;
    private FixHistory fixHistory;
    private PromptBuilder promptBuilder;
    private AiProvider aiProvider;
    private DiffApplier diffApplier;
    private CompilerRunner compilerRunner;
    private DiffPrinter diffPrinter;
    private MigrationProperties props;

    private DefaultMigrationEngine engine;

    @BeforeEach
    void setUp() throws IOException {
        sourceLoader     = mock(SourceLoader.class);
        knowledgeLoader  = mock(KnowledgeLoader.class);
        similaritySearch = mock(SimilaritySearch.class);
        fixHistory       = mock(FixHistory.class);
        promptBuilder    = mock(PromptBuilder.class);
        aiProvider       = mock(AiProvider.class);
        diffApplier      = mock(DiffApplier.class);
        compilerRunner   = mock(CompilerRunner.class);
        diffPrinter      = mock(DiffPrinter.class);
        props            = mock(MigrationProperties.class);

        when(props.projectRoot()).thenReturn(tempDir);
        when(knowledgeLoader.findRelevant(any())).thenReturn(List.of());
        when(similaritySearch.findCandidates(any())).thenReturn(List.of());
        when(fixHistory.findSimilar(any())).thenReturn(List.of());
        when(aiProvider.providerName()).thenReturn("MOCK");
        when(promptBuilder.build(any())).thenReturn(new MigrationPrompt("sys", "usr", 100));

        engine = new DefaultMigrationEngine(
                sourceLoader, knowledgeLoader, similaritySearch, fixHistory,
                promptBuilder, aiProvider, diffApplier, compilerRunner, diffPrinter, props);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private SessionState sessionWithError() throws IOException {
        Path sourceFile = tempDir.resolve("Foo.java");
        Files.writeString(sourceFile, "class Foo { Old obj; }");

        CompilerError error = new CompilerError(sourceFile, 1, 13, "cannot find symbol",
                List.of("symbol: class Old"));
        CompilationResult result = new CompilationResult(false, List.of(error), "raw", Duration.ZERO);

        SessionState session = new SessionState();
        session.updateCompilation(result);
        return session;
    }

    private SourceSnippet snippet(Path sourceFile) {
        return new SourceSnippet(sourceFile, List.of(), "class Foo", null,
                List.of(new NumberedLine(1, "class Foo { Old obj; }")), 1);
    }

    private AiResponse diffResponse(String diff) {
        return new AiResponse(diff, false, null, diff, "MOCK", 100, 50);
    }

    private AiResponse needsContextResponse(String request) {
        return new AiResponse("NEED_CONTEXT: " + request, true, request, null, "MOCK", 50, 10);
    }

    private CompilationResult success() {
        return new CompilationResult(true, List.of(), "", Duration.ZERO);
    }

    private CompilationResult failure(int errors) {
        List<CompilerError> errs = new java.util.ArrayList<>();
        for (int i = 0; i < errors; i++) {
            errs.add(new CompilerError(Path.of("X.java"), i + 1, 1, "error", List.of()));
        }
        return new CompilationResult(false, errs, "", Duration.ZERO);
    }

    private FixOutcome run(SessionState session, String userInput) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        return engine.fixNextError(session,
                new BufferedReader(new StringReader(userInput)),
                new PrintStream(baos));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void returnsNoErrorWhenSessionHasNoCurrentError() throws IOException {
        SessionState session = new SessionState(); // no compile run

        FixOutcome outcome = run(session, "");

        assertThat(outcome).isInstanceOf(FixOutcome.NoError.class);
        verify(sourceLoader, never()).load(any());
    }

    @Test
    void returnsApplyFailedWhenSourceCannotBeLoaded() throws IOException {
        SessionState session = sessionWithError();
        when(sourceLoader.load(any()))
                .thenThrow(new SourceLoadException("file gone", null));

        FixOutcome outcome = run(session, "");

        assertThat(outcome).isInstanceOf(FixOutcome.ApplyFailed.class);
        assertThat(((FixOutcome.ApplyFailed) outcome).reason()).contains("Cannot load source");
    }

    @Test
    void returnsNeedsContextWhenAIRequests() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(needsContextResponse("Missing JAR index"));

        FixOutcome outcome = run(session, "");

        assertThat(outcome).isInstanceOf(FixOutcome.NeedsContext.class);
        assertThat(((FixOutcome.NeedsContext) outcome).request()).contains("Missing JAR index");
    }

    @Test
    void returnsApplyFailedWhenAIReturnsEmptyDiff() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any()))
                .thenReturn(new AiResponse("", false, null, "", "MOCK", 10, 5));

        FixOutcome outcome = run(session, "");

        assertThat(outcome).isInstanceOf(FixOutcome.ApplyFailed.class);
        assertThat(((FixOutcome.ApplyFailed) outcome).reason()).containsIgnoringCase("empty");
    }

    @Test
    void returnsApplyFailedWhenAIThrowsException() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenThrow(new AiException("network error"));

        FixOutcome outcome = run(session, "");

        assertThat(outcome).isInstanceOf(FixOutcome.ApplyFailed.class);
        assertThat(((FixOutcome.ApplyFailed) outcome).reason()).contains("AI call failed");
    }

    @Test
    void returnsRejectedWhenUserDeclinesWithN() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse("@@ -1 +1 @@\n-old\n+new\n"));

        FixOutcome outcome = run(session, "n\n");

        assertThat(outcome).isInstanceOf(FixOutcome.Rejected.class);
        verify(diffApplier, never()).apply(any(), any());
    }

    @Test
    void returnsRejectedWhenUserPressesEnterOnly() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse("@@ -1 +1 @@\n-old\n+new\n"));

        FixOutcome outcome = run(session, "\n");

        assertThat(outcome).isInstanceOf(FixOutcome.Rejected.class);
    }

    @Test
    void returnsApplyFailedWhenDiffDoesNotApplyCleanly() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse("@@ -1 +1 @@\n-old\n+new\n"));
        when(diffApplier.apply(any(), any()))
                .thenReturn(new ApplyResult(false, "line mismatch at 1"));

        FixOutcome outcome = run(session, "y\n");

        assertThat(outcome).isInstanceOf(FixOutcome.ApplyFailed.class);
        assertThat(((FixOutcome.ApplyFailed) outcome).reason()).contains("Diff apply failed");
    }

    @Test
    void successPathAppliesDiffAndRecompiles() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse("@@ -1 +1 @@\n-Old\n+New\n"));
        when(diffApplier.apply(any(), any())).thenReturn(new ApplyResult(true, "Applied 1 hunk(s)"));
        when(compilerRunner.compile(any())).thenReturn(success());

        FixOutcome outcome = run(session, "y\n");

        assertThat(outcome).isInstanceOf(FixOutcome.Success.class);
        verify(fixHistory).record(any());
    }

    @Test
    void recordsFixEvenWhenRecompileFails() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse("@@ -1 +1 @@\n-Old\n+New\n"));
        when(diffApplier.apply(any(), any())).thenReturn(new ApplyResult(true, "Applied 1 hunk(s)"));
        when(compilerRunner.compile(any())).thenReturn(failure(3));

        FixOutcome outcome = run(session, "y\n");

        assertThat(outcome).isInstanceOf(FixOutcome.Success.class);
        FixOutcome.Success success = (FixOutcome.Success) outcome;
        assertThat(success.fix().compilationSucceeded()).isFalse();
        verify(fixHistory).record(any());
    }

    @Test
    void updatesSessionAfterRecompile() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse("@@ -1 +1 @@\n-Old\n+New\n"));
        when(diffApplier.apply(any(), any())).thenReturn(new ApplyResult(true, "Applied 1 hunk(s)"));
        when(compilerRunner.compile(any())).thenReturn(success());

        run(session, "y\n");

        assertThat(session.lastCompilation().success()).isTrue();
    }

    @Test
    void fixRecordContainsExpectedFields() throws IOException {
        SessionState session = sessionWithError();
        Path sourceFile = session.currentError().get().sourceFile();
        String diff = "@@ -1 +1 @@\n-Old\n+New\n";
        when(sourceLoader.load(any())).thenReturn(snippet(sourceFile));
        when(aiProvider.complete(any())).thenReturn(diffResponse(diff));
        when(diffApplier.apply(any(), any())).thenReturn(new ApplyResult(true, "Applied 1 hunk(s)"));
        when(compilerRunner.compile(any())).thenReturn(success());

        FixOutcome outcome = run(session, "y\n");

        FixRecord fix = ((FixOutcome.Success) outcome).fix();
        assertThat(fix.errorMessage()).isEqualTo("cannot find symbol");
        assertThat(fix.diffPatch()).isEqualTo(diff);
        assertThat(fix.compilationSucceeded()).isTrue();
        assertThat(fix.appliedAt()).isNotBlank();
    }
}
