package com.bank.migration.similarity;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.jar.JarIndexStore;
import com.bank.migration.TestJarLocator;
import com.bank.migration.jar.ReflectionJarIndexer;
import com.bank.migration.jar.model.JarIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test that builds a real JAR index from commons-lang3 and searches it.
 * Verifies end-to-end: CompilerError → SearchQuery → scoring → ranked candidates.
 */
class JarSimilaritySearchTest {

    private static JarIndex commonsIndex;

    @BeforeAll
    static void buildIndex() {
        Path jar = TestJarLocator.findJar("commons-lang3");
        commonsIndex = new ReflectionJarIndexer().index(jar);
    }

    private JarSimilaritySearch searchFor(JarIndex... indexes) {
        JarIndexStore store = mock(JarIndexStore.class);
        when(store.loadAll()).thenReturn(List.of(indexes));
        return new JarSimilaritySearch(store, new QueryExtractor(), new CandidateScorer());
    }

    private CompilerError classError(String className) {
        return new CompilerError(
                Path.of("/p/Foo.java"), 1, 1,
                "cannot find symbol",
                List.of("symbol:   class " + className, "location: class com.example.Foo"));
    }

    private CompilerError methodError(String methodName) {
        return new CompilerError(
                Path.of("/p/Foo.java"), 1, 1,
                "cannot find symbol",
                List.of("symbol:   method " + methodName + "(java.lang.String)",
                        "location: class com.example.Foo"));
    }

    // ── Sanity: commons-lang3 classes are findable ─────────────────────────

    @Test
    void findsStringUtilsByExactName() {
        JarSimilaritySearch search = searchFor(commonsIndex);

        List<CandidateMatch> results = search.findCandidates(classError("StringUtils"));

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().candidateClass().simpleName()).isEqualTo("StringUtils");
        assertThat(results.getFirst().confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void findsNumberUtilsByExactName() {
        JarSimilaritySearch search = searchFor(commonsIndex);

        List<CandidateMatch> results = search.findCandidates(classError("NumberUtils"));

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void returnsTopKResults() {
        // "Utils" appears in many commons-lang3 class names
        JarSimilaritySearch search = searchFor(commonsIndex);
        CompilerError e = new CompilerError(Path.of("/p/Foo.java"), 1, 1,
                "cannot find symbol",
                List.of("symbol:   class StringUtils", "location: class Foo"));

        List<CandidateMatch> results = search.findCandidates(e, 2);

        assertThat(results).hasSize(2);
    }

    @Test
    void resultsAreSortedDescendingByScore() {
        JarSimilaritySearch search = searchFor(commonsIndex);

        List<CandidateMatch> results = search.findCandidates(classError("StringUtils"), 5);

        for (int i = 0; i < results.size() - 1; i++) {
            assertThat(results.get(i).score()).isGreaterThanOrEqualTo(results.get(i + 1).score());
        }
    }

    @Test
    void findsMethodByName() {
        JarSimilaritySearch search = searchFor(commonsIndex);

        // isBlank is a well-known method on StringUtils
        List<CandidateMatch> results = search.findCandidates(methodError("isBlank"), 10);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().candidateMethod()).isNotNull();
        assertThat(results.getFirst().candidateMethod().name()).isEqualTo("isBlank");
    }

    // ── Edge cases ───────────────────────────────────────────────────────────

    @Test
    void returnsEmptyWhenNoIndexesLoaded() {
        JarIndexStore emptyStore = mock(JarIndexStore.class);
        when(emptyStore.loadAll()).thenReturn(List.of());
        JarSimilaritySearch search = new JarSimilaritySearch(
                emptyStore, new QueryExtractor(), new CandidateScorer());

        List<CandidateMatch> results = search.findCandidates(classError("ActiveMonitor"));

        assertThat(results).isEmpty();
    }

    @Test
    void returnsEmptyForUnparsableError() {
        JarSimilaritySearch search = searchFor(commonsIndex);
        CompilerError e = new CompilerError(Path.of("/p/Foo.java"), 1, 1,
                "';' expected", List.of());

        assertThat(search.findCandidates(e)).isEmpty();
    }

    @Test
    void returnsEmptyWhenNothingMatchesAboveThreshold() {
        JarSimilaritySearch search = searchFor(commonsIndex);
        // "ZzzzXxxxQqqq" won't match anything in commons-lang3
        List<CandidateMatch> results = search.findCandidates(classError("ZzzzXxxxQqqq"));
        assertThat(results).isEmpty();
    }

    @Test
    void candidateMatchHasNonBlankReason() {
        JarSimilaritySearch search = searchFor(commonsIndex);
        List<CandidateMatch> results = search.findCandidates(classError("StringUtils"));
        assertThat(results).isNotEmpty();
        results.forEach(m -> assertThat(m.reason()).isNotBlank());
    }

    @Test
    void toPromptTextContainsFqnAndConfidence() {
        JarSimilaritySearch search = searchFor(commonsIndex);
        List<CandidateMatch> results = search.findCandidates(classError("StringUtils"));
        assertThat(results).isNotEmpty();
        String prompt = results.getFirst().toPromptText();
        assertThat(prompt).contains("StringUtils");
        assertThat(prompt).contains("HIGH");
    }
}
