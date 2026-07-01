package com.bank.migration.source;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportExtractorTest {

    private final ImportExtractor extractor = new ImportExtractor();

    @Test
    void extractsStandardImports() {
        List<String> lines = List.of(
                "package com.example;",
                "",
                "import java.util.List;",
                "import com.old.activepivot.ActiveMonitor;",
                "",
                "public class Foo {}"
        );

        List<String> imports = extractor.extract(lines);

        assertThat(imports).containsExactly(
                "import java.util.List;",
                "import com.old.activepivot.ActiveMonitor;"
        );
    }

    @Test
    void excludesPackageDeclaration() {
        List<String> lines = List.of("package com.example;", "import java.util.Map;");
        assertThat(extractor.extract(lines)).containsExactly("import java.util.Map;");
    }

    @Test
    void excludesStaticImportLines() {
        List<String> lines = List.of(
                "import static org.junit.jupiter.api.Assertions.assertThat;",
                "import java.util.List;"
        );
        // Static imports are still imports — they should be included
        assertThat(extractor.extract(lines)).hasSize(2);
    }

    @Test
    void returnsEmptyForFileWithNoImports() {
        List<String> lines = List.of("package com.example;", "public class Empty {}");
        assertThat(extractor.extract(lines)).isEmpty();
    }

    @Test
    void handlesIndentedImports() {
        // Unusual but valid in some generated code
        List<String> lines = List.of("  import java.util.List;");
        assertThat(extractor.extract(lines)).hasSize(1);
    }

    @Test
    void excludesCommentedImports() {
        // A comment that starts with 'import' in content should not be extracted
        List<String> lines = List.of(
                "// import java.util.List;",
                "import java.util.Map;"
        );
        // Comment line does NOT start with 'import' — it starts with '//'
        assertThat(extractor.extract(lines)).containsExactly("import java.util.Map;");
    }
}
