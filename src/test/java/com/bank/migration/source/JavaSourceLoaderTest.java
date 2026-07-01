package com.bank.migration.source;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JavaSourceLoaderTest {

    @TempDir
    Path tempDir;

    private MigrationProperties props;
    private JavaSourceLoader loader;

    @BeforeEach
    void setUp() {
        props = mock(MigrationProperties.class);
        when(props.sourceSurroundingLines()).thenReturn(5);
        when(props.projectRoot()).thenReturn(tempDir);
        loader = new JavaSourceLoader(props, new ImportExtractor(), new MethodDetector());
    }

    private Path writeJavaFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private CompilerError errorAt(Path file, int line) {
        return new CompilerError(file, line, 1, "cannot find symbol", List.of());
    }

    @Test
    void extractsImportsFromFile() throws Exception {
        Path file = writeJavaFile("Foo.java", """
                package com.example;
                import java.util.List;
                import com.old.ActiveMonitor;
                public class Foo {
                    public void run() {
                        ActiveMonitor m = new ActiveMonitor();
                    }
                }
                """);

        SourceSnippet snippet = loader.load(errorAt(file, 6));

        assertThat(snippet.imports()).containsExactly(
                "import java.util.List;",
                "import com.old.ActiveMonitor;"
        );
    }

    @Test
    void extractsClassDeclaration() throws Exception {
        Path file = writeJavaFile("Bar.java", """
                package com.example;
                public class Bar extends BaseService implements Runnable {
                    public void run() {
                        OldApi.call();
                    }
                }
                """);

        SourceSnippet snippet = loader.load(errorAt(file, 4));

        assertThat(snippet.classDeclaration()).contains("class Bar");
    }

    @Test
    void windowContainsErrorLine() throws Exception {
        Path file = writeJavaFile("Baz.java", """
                package com.example;
                public class Baz {
                    public void go() {
                        System.out.println("line 4");
                        OldApi.call();
                        System.out.println("line 6");
                    }
                }
                """);

        SourceSnippet snippet = loader.load(errorAt(file, 5));

        assertThat(snippet.errorLine()).isEqualTo(5);
        assertThat(snippet.lines()).extracting(NumberedLine::number).contains(5);
    }

    @Test
    void windowIsClampedToFileBounds() throws Exception {
        Path file = writeJavaFile("Small.java", """
                public class Small {
                    public void m() { OldApi.x(); }
                }
                """);

        // surrounding=5 but file only has 3 lines — should not throw
        SourceSnippet snippet = loader.load(errorAt(file, 2));

        assertThat(snippet.lines()).hasSize(3);
        assertThat(snippet.lines().getFirst().number()).isEqualTo(1);
        assertThat(snippet.lines().getLast().number()).isEqualTo(3);
    }

    @Test
    void detectsEnclosingMethod() throws Exception {
        Path file = writeJavaFile("Svc.java", """
                public class Svc {
                    public String compute(int x) {
                        return OldCompute.run(x);
                    }
                }
                """);

        SourceSnippet snippet = loader.load(errorAt(file, 3));

        assertThat(snippet.methodSignature()).isNotNull();
        assertThat(snippet.methodSignature()).contains("compute");
    }

    @Test
    void promptTextContainsKeyParts() throws Exception {
        Path file = writeJavaFile("Api.java", """
                package com.example;
                import com.old.OldClient;
                public class Api {
                    public void call() {
                        OldClient.invoke();
                    }
                }
                """);

        SourceSnippet snippet = loader.load(errorAt(file, 5));
        String prompt = snippet.toPromptText();

        assertThat(prompt).contains("Api.java");
        assertThat(prompt).contains("import com.old.OldClient;");
        assertThat(prompt).contains("<<< ERROR");
    }

    @Test
    void throwsSourceLoadExceptionForMissingFile() {
        Path missing = tempDir.resolve("DoesNotExist.java");
        CompilerError error = new CompilerError(missing, 1, 1, "error", List.of());

        assertThatThrownBy(() -> loader.load(error))
                .isInstanceOf(SourceLoadException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void lineNumbersInWindowAre1Based() throws Exception {
        Path file = writeJavaFile("Nums.java", """
                public class Nums {
                    void m() { OldApi.x(); }
                }
                """);

        SourceSnippet snippet = loader.load(errorAt(file, 2));

        assertThat(snippet.lines().getFirst().number()).isGreaterThanOrEqualTo(1);
        snippet.lines().forEach(l -> assertThat(l.number()).isPositive());
    }
}
