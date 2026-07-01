package com.bank.migration.prompt;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.fix.FixRecord;
import com.bank.migration.knowledge.KnowledgeMatch;
import com.bank.migration.similarity.CandidateMatch;
import com.bank.migration.similarity.Confidence;
import com.bank.migration.source.SourceSnippet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

/**
 * Builds a compact {@link MigrationPrompt} that stays within the configured token budget.
 *
 * <p>Section priority (highest to lowest):
 * <ol>
 *   <li>Compiler error   — always included, never trimmed</li>
 *   <li>Source snippet   — always included, never trimmed</li>
 *   <li>Previous fixes   — included first when available (proven solutions first)</li>
 *   <li>JAR candidates   — trimmed to fit remaining budget</li>
 *   <li>Knowledge docs   — trimmed to fit remaining budget</li>
 * </ol>
 *
 * <p>Token estimation uses the rough rule: 1 token ≈ 4 characters of English/Java text.
 * This is conservative enough to avoid exceeding API limits in practice.
 */
@Component
public class CompactPromptBuilder implements PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(CompactPromptBuilder.class);

    /** Tokens reserved for the system prompt and instructions section. */
    private static final int OVERHEAD_TOKENS = 300;

    private final MigrationProperties props;

    public CompactPromptBuilder(MigrationProperties props) {
        this.props = props;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @Override
    public MigrationPrompt build(PromptContext ctx) {
        int budget = props.ai().maxInputTokens() - OVERHEAD_TOKENS;

        String systemPrompt = buildSystemPrompt();
        String userPrompt   = buildUserPrompt(ctx, budget);

        int estimated = estimate(systemPrompt) + estimate(userPrompt);
        log.debug("Prompt built: ~{} tokens (budget {})", estimated, props.ai().maxInputTokens());

        return new MigrationPrompt(systemPrompt, userPrompt, estimated);
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    private String buildSystemPrompt() {
        return """
                You are an ActivePivot migration assistant. Your sole job is to fix Java \
                compiler errors caused by API changes between ActivePivot versions.

                RULES — follow every rule without exception:
                1. Only use classes and methods listed in "JAR CANDIDATES" or "MIGRATION KNOWLEDGE".
                2. Never guess, invent, or hallucinate API names.
                3. If you cannot fix the error with the provided context, respond with exactly:
                   NEED_CONTEXT: <one sentence describing what additional information is required>
                4. Otherwise, respond with ONLY a unified diff in this exact format:
                   --- a/<file-path>
                   +++ b/<file-path>
                   @@ -N,M +N,M @@
                    context
                   -removed
                   +added
                5. No explanation. Diff only. The diff must apply cleanly to the source shown.""";
    }

    // ── User prompt ───────────────────────────────────────────────────────────

    private String buildUserPrompt(PromptContext ctx, int budget) {
        StringBuilder sb = new StringBuilder();

        // ── Mandatory: error (always fits) ────────────────────────────────────
        String errorSection = buildErrorSection(ctx.error());
        sb.append(errorSection);
        budget -= estimate(errorSection);

        // ── Mandatory: source snippet ─────────────────────────────────────────
        String sourceSection = buildSourceSection(ctx.sourceSnippet());
        sb.append(sourceSection);
        budget -= estimate(sourceSection);

        if (budget <= 0) {
            log.warn("Token budget exhausted after mandatory sections — prompt will be minimal");
            return sb.toString();
        }

        // ── Previous fixes (highest priority of optional sections) ────────────
        if (!ctx.previousFixes().isEmpty()) {
            String fixes = buildFixesSection(ctx.previousFixes(), budget / 2);
            if (!fixes.isBlank()) {
                sb.append(fixes);
                budget -= estimate(fixes);
            }
        }

        // ── JAR candidates ────────────────────────────────────────────────────
        if (!ctx.jarCandidates().isEmpty()) {
            String candidates = buildCandidatesSection(ctx.jarCandidates(), budget / 2);
            if (!candidates.isBlank()) {
                sb.append(candidates);
                budget -= estimate(candidates);
            }
        }

        // ── Knowledge docs ────────────────────────────────────────────────────
        if (!ctx.knowledgeMatches().isEmpty()) {
            String knowledge = buildKnowledgeSection(ctx.knowledgeMatches(), budget);
            if (!knowledge.isBlank()) {
                sb.append(knowledge);
            }
        }

        // ── Warning when no candidates at all ─────────────────────────────────
        if (ctx.jarCandidates().isEmpty() && ctx.knowledgeMatches().isEmpty()
                && ctx.previousFixes().isEmpty()) {
            sb.append("\n⚠ No replacement candidates found in JAR index or knowledge docs.")
              .append(" Respond with NEED_CONTEXT.\n");
        }

        return sb.toString();
    }

    // ── Section builders ──────────────────────────────────────────────────────

    private String buildErrorSection(CompilerError error) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n== COMPILER ERROR ==\n");
        sb.append("File  : ").append(error.sourceFile()).append("\n");
        sb.append("Line  : ").append(error.line())
          .append(", Column: ").append(error.column()).append("\n");
        sb.append("Error : ").append(error.message()).append("\n");
        if (!error.details().isEmpty()) {
            error.details().forEach(d -> sb.append("        ").append(d.strip()).append("\n"));
        }
        return sb.toString();
    }

    private String buildSourceSection(SourceSnippet snippet) {
        return "\n== SOURCE CODE ==\n" + snippet.toPromptText();
    }

    private String buildCandidatesSection(List<CandidateMatch> candidates, int tokenBudget) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n== JAR CANDIDATES ==\n");

        boolean hasHighConfidence = candidates.stream()
                .anyMatch(c -> c.confidence() == Confidence.HIGH);
        if (!hasHighConfidence) {
            sb.append("⚠ No HIGH-confidence candidates found.\n");
        }

        int remaining = tokenBudget;
        for (CandidateMatch c : candidates) {
            String entry = c.toPromptText() + "\n";
            if (estimate(entry) > remaining) break;
            sb.append(entry);
            remaining -= estimate(entry);
        }
        return sb.toString();
    }

    private String buildKnowledgeSection(List<KnowledgeMatch> matches, int tokenBudget) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n== MIGRATION KNOWLEDGE ==\n");

        int remaining = tokenBudget;
        for (KnowledgeMatch m : matches) {
            String entry = m.toPromptText() + "\n";
            if (estimate(entry) > remaining) {
                sb.append("[... additional knowledge sections omitted to stay within token budget ...]\n");
                break;
            }
            sb.append(entry);
            remaining -= estimate(entry);
        }
        return sb.toString();
    }

    private String buildFixesSection(List<FixRecord> fixes, int tokenBudget) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n== PREVIOUS SUCCESSFUL FIXES ==\n");

        int remaining = tokenBudget;
        for (FixRecord fix : fixes) {
            String entry = buildFixEntry(fix);
            if (estimate(entry) > remaining) break;
            sb.append(entry);
            remaining -= estimate(entry);
        }
        return sb.toString();
    }

    private String buildFixEntry(FixRecord fix) {
        return "Error: " + fix.errorMessage() + "\n"
             + "Applied: " + fix.appliedAt() + "\n"
             + "Diff:\n" + fix.diffPatch() + "\n---\n";
    }

    // ── Token estimation ──────────────────────────────────────────────────────

    /** Rough estimate: 1 token ≈ 4 chars. Conservative for mixed Java/English text. */
    static int estimate(String text) {
        return Math.max(1, text.length() / 4);
    }
}
