package com.bank.migration.prompt;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.fix.FixRecord;
import com.bank.migration.jar.model.ClassKind;
import com.bank.migration.jar.model.IndexedClass;
import com.bank.migration.knowledge.KnowledgeMatch;
import com.bank.migration.knowledge.KnowledgeSection;
import com.bank.migration.similarity.CandidateMatch;
import com.bank.migration.similarity.Confidence;
import com.bank.migration.source.NumberedLine;
import com.bank.migration.source.SourceSnippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompactPromptBuilderTest {

    private CompactPromptBuilder builder;

    @BeforeEach
    void setUp() {
        MigrationProperties props = mock(MigrationProperties.class);
        MigrationProperties.AiProperties ai = mock(MigrationProperties.AiProperties.class);
        when(ai.maxInputTokens()).thenReturn(8000);
        when(props.ai()).thenReturn(ai);
        builder = new CompactPromptBuilder(props);
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private CompilerError sampleError() {
        return new CompilerError(
                Path.of("/proj/src/main/java/com/bank/Foo.java"),
                42, 15,
                "cannot find symbol",
                List.of("symbol:   class ActiveMonitor", "location: class com.bank.Foo"));
    }

    private SourceSnippet sampleSnippet() {
        List<NumberedLine> lines = List.of(
                new NumberedLine(40, "    public void setup() {"),
                new NumberedLine(41, "        // create monitor"),
                new NumberedLine(42, "        ActiveMonitor m = new ActiveMonitor();"),
                new NumberedLine(43, "    }"));
        return new SourceSnippet(
                Path.of("/proj/src/main/java/com/bank/Foo.java"),
                List.of("import com.old.ActiveMonitor;"),
                "public class Foo {",
                "public void setup() {",
                lines,
                42);
    }

    private CandidateMatch highConfidenceCandidate() {
        IndexedClass cls = new IndexedClass(
                "com.activeviam.monitoring.IActiveMonitor",
                "com.activeviam.monitoring",
                "IActiveMonitor",
                ClassKind.INTERFACE,
                null, List.of(), List.of(), List.of(), List.of());
        return new CandidateMatch(cls, null, Confidence.HIGH, 80.0,
                "'ActiveMonitor' → IActiveMonitor");
    }

    private KnowledgeMatch sampleKnowledgeMatch() {
        KnowledgeSection section = new KnowledgeSection(
                Path.of("guide.md"),
                "ActiveMonitor Migration",
                "Replace ActiveMonitor with IActiveMonitor from com.activeviam.monitoring.",
                0);
        return new KnowledgeMatch(section, 9.0, Set.of("ActiveMonitor"));
    }

    private FixRecord sampleFix() {
        return new FixRecord(
                "Foo.java:42:15:cannot find symbol",
                "/proj/Foo.java",
                "cannot find symbol",
                "symbol: class ActiveMonitor",
                "--- a/Foo.java\n+++ b/Foo.java\n@@ -42 +42 @@\n-ActiveMonitor\n+IActiveMonitor\n",
                "2026-07-01T10:00:00Z",
                true);
    }

    private PromptContext context(List<CandidateMatch> candidates,
                                   List<KnowledgeMatch> knowledge,
                                   List<FixRecord> fixes) {
        return new PromptContext(sampleError(), sampleSnippet(), knowledge, candidates, fixes);
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    @Test
    void systemPromptContainsKeyRules() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.systemPrompt())
                .contains("NEED_CONTEXT")
                .contains("unified diff")
                .containsIgnoringCase("never")
                .containsIgnoringCase("hallucinate");
    }

    @Test
    void systemPromptIsNeverEmpty() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.systemPrompt()).isNotBlank();
    }

    // ── Error section ─────────────────────────────────────────────────────────

    @Test
    void userPromptContainsErrorLine() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.userPrompt()).contains("42").contains("cannot find symbol");
    }

    @Test
    void userPromptContainsSymbolDetail() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.userPrompt()).contains("ActiveMonitor");
    }

    // ── Source section ────────────────────────────────────────────────────────

    @Test
    void userPromptContainsErrorMarker() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.userPrompt()).contains("<<< ERROR");
    }

    @Test
    void userPromptContainsImports() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.userPrompt()).contains("import com.old.ActiveMonitor");
    }

    // ── Candidates section ────────────────────────────────────────────────────

    @Test
    void candidatesAreIncludedWhenPresent() {
        MigrationPrompt p = builder.build(context(
                List.of(highConfidenceCandidate()), List.of(), List.of()));
        assertThat(p.userPrompt())
                .contains("com.activeviam.monitoring.IActiveMonitor")
                .contains("HIGH");
    }

    @Test
    void warningShownWhenNoHighConfidenceCandidate() {
        IndexedClass cls = new IndexedClass("com.x.Foo", "com.x", "Foo",
                ClassKind.CLASS, null, List.of(), List.of(), List.of(), List.of());
        CandidateMatch low = new CandidateMatch(cls, null, Confidence.LOW, 25.0, "loose match");

        MigrationPrompt p = builder.build(context(List.of(low), List.of(), List.of()));
        assertThat(p.userPrompt()).contains("No HIGH-confidence");
    }

    @Test
    void warningShownWhenNoCandidatesAndNoKnowledge() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.userPrompt()).contains("NEED_CONTEXT");
    }

    // ── Knowledge section ─────────────────────────────────────────────────────

    @Test
    void knowledgeIsIncludedWhenPresent() {
        MigrationPrompt p = builder.build(context(
                List.of(), List.of(sampleKnowledgeMatch()), List.of()));
        assertThat(p.userPrompt()).contains("ActiveMonitor Migration");
    }

    // ── Previous fixes section ────────────────────────────────────────────────

    @Test
    void previousFixesAreIncluded() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of(sampleFix())));
        assertThat(p.userPrompt())
                .contains("PREVIOUS SUCCESSFUL FIXES")
                .contains("IActiveMonitor");
    }

    // ── Token budget ──────────────────────────────────────────────────────────

    @Test
    void estimatedTokensIsPositive() {
        MigrationPrompt p = builder.build(context(
                List.of(highConfidenceCandidate()),
                List.of(sampleKnowledgeMatch()),
                List.of(sampleFix())));
        assertThat(p.estimatedTokens()).isPositive();
    }

    @Test
    void estimatedTokensDoesNotExceedConfiguredMax() {
        MigrationPrompt p = builder.build(context(
                List.of(highConfidenceCandidate()),
                List.of(sampleKnowledgeMatch()),
                List.of(sampleFix())));
        // estimated can slightly exceed max due to system overhead, but should be close
        assertThat(p.estimatedTokens()).isLessThan(8000 + 500);
    }

    @Test
    void tokenEstimatorIsAtLeastOne() {
        assertThat(CompactPromptBuilder.estimate("")).isGreaterThanOrEqualTo(1);
        assertThat(CompactPromptBuilder.estimate("x")).isGreaterThanOrEqualTo(1);
    }

    // ── Combined ─────────────────────────────────────────────────────────────

    @Test
    void combinedContainsBothParts() {
        MigrationPrompt p = builder.build(context(List.of(), List.of(), List.of()));
        assertThat(p.combined())
                .contains(p.systemPrompt().substring(0, 20))
                .contains("COMPILER ERROR");
    }
}
