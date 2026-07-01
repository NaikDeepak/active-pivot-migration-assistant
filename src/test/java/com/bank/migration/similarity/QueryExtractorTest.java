package com.bank.migration.similarity;

import com.bank.migration.compiler.CompilerError;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryExtractorTest {

    private final QueryExtractor extractor = new QueryExtractor();

    private CompilerError error(String message, List<String> details) {
        return new CompilerError(Path.of("/p/Foo.java"), 1, 1, message, details);
    }

    @Test
    void extractsClassNameFromSymbolDetail() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   class ActiveMonitor", "location: class com.example.Foo"));

        SearchQuery q = extractor.extract(e);

        assertThat(q.missingClassName()).isEqualTo("ActiveMonitor");
        assertThat(q.isClassSearch()).isTrue();
    }

    @Test
    void extractsMethodNameAndParams() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   method computeRisk(java.lang.String,int)",
                        "location: class com.example.RiskService"));

        SearchQuery q = extractor.extract(e);

        assertThat(q.missingMethodName()).isEqualTo("computeRisk");
        assertThat(q.missingMethodParams()).containsExactly("java.lang.String", "int");
        assertThat(q.isMethodSearch()).isTrue();
    }

    @Test
    void extractsZeroParamMethod() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   method start()"));

        SearchQuery q = extractor.extract(e);

        assertThat(q.missingMethodName()).isEqualTo("start");
        assertThat(q.missingMethodParams()).isEmpty();
    }

    @Test
    void extractsMissingPackage() {
        CompilerError e = error("package com.qfs.monitoring does not exist", List.of());

        SearchQuery q = extractor.extract(e);

        assertThat(q.isPackageSearch()).isTrue();
        assertThat(q.missingPackage()).isEqualTo("com.qfs.monitoring");
    }

    @Test
    void extractsLocationClass() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   class OldApi", "location: class com.bank.service.CubeService"));

        SearchQuery q = extractor.extract(e);

        assertThat(q.locationClass()).isEqualTo("com.bank.service.CubeService");
    }

    @Test
    void emptyQueryWhenNoUsefulInfo() {
        CompilerError e = error("';' expected", List.of());

        SearchQuery q = extractor.extract(e);

        assertThat(q.isEmpty()).isTrue();
    }

    @Test
    void classAndLocationExtractedTogether() {
        CompilerError e = error("cannot find symbol",
                List.of("symbol:   class IActivePivotManager",
                        "location: class com.bank.cube.CubeBuilder"));

        SearchQuery q = extractor.extract(e);

        assertThat(q.missingClassName()).isEqualTo("IActivePivotManager");
        assertThat(q.locationClass()).isEqualTo("com.bank.cube.CubeBuilder");
    }
}
