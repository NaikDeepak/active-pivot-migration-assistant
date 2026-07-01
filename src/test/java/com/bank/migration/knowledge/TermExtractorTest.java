package com.bank.migration.knowledge;

import com.bank.migration.compiler.CompilerError;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TermExtractorTest {

    private final TermExtractor extractor = new TermExtractor();

    private CompilerError error(String message, List<String> details) {
        return new CompilerError(Path.of("/p/Foo.java"), 1, 1, message, details);
    }

    @Test
    void extractsClassNameFromSymbolDetail() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   class ActiveMonitor", "location: class com.example.Foo"));

        Set<String> terms = extractor.extract(e);

        assertThat(terms).contains("ActiveMonitor");
    }

    @Test
    void splitsCamelCaseIdentifiers() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   class ActiveMonitor"));

        Set<String> terms = extractor.extract(e);

        assertThat(terms).contains("Active", "Monitor");
    }

    @Test
    void filtersOutStopWords() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   class ActiveMonitor"));

        Set<String> terms = extractor.extract(e);

        assertThat(terms).doesNotContain("cannot", "find", "symbol", "class");
    }

    @Test
    void filtersShortTerms() {
        CompilerError e = error("';' expected", List.of());

        Set<String> terms = extractor.extract(e);

        terms.forEach(t -> assertThat(t.length()).isGreaterThanOrEqualTo(3));
    }

    @Test
    void extractsMethodNameFromSymbolDetail() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   method computeRisk(java.lang.String)"));

        Set<String> terms = extractor.extract(e);

        assertThat(terms).contains("computeRisk");
    }

    @Test
    void extractsTermsFromPackageMissingError() {
        CompilerError e = error("package com.qfs.monitoring does not exist", List.of());

        Set<String> terms = extractor.extract(e);

        assertThat(terms).contains("monitoring");
    }

    @Test
    void returnsNonEmptySetForTypicalActivePivotError() {
        CompilerError e = error("cannot find symbol",
                List.of(
                        "symbol:   class IActivePivotManager",
                        "location: class com.bank.service.CubeService"
                ));

        Set<String> terms = extractor.extract(e);

        assertThat(terms).isNotEmpty();
        assertThat(terms).contains("IActivePivotManager");
    }
}
