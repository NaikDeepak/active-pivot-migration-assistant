package com.bank.migration.source;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MethodDetectorTest {

    private final MethodDetector detector = new MethodDetector();

    @Test
    void detectsPublicMethod() {
        List<String> lines = List.of(
                "public class Foo {",
                "    public void process(String input) {",
                "        OldApi.call(input);",   // error line (idx 2)
                "    }",
                "}"
        );
        String sig = detector.detect(lines, 2);
        assertThat(sig).contains("process");
    }

    @Test
    void detectsPrivateStaticMethod() {
        List<String> lines = List.of(
                "class Foo {",
                "    private static List<String> build(int size) {",
                "        return OldBuilder.create(size);",  // error line (idx 2)
                "    }",
                "}"
        );
        String sig = detector.detect(lines, 2);
        assertThat(sig).contains("build");
    }

    @Test
    void detectsConstructor() {
        List<String> lines = List.of(
                "public class Service {",
                "    public Service(Repository repo) {",
                "        this.repo = OldRepo.wrap(repo);",  // error line (idx 2)
                "    }",
                "}"
        );
        String sig = detector.detect(lines, 2);
        assertThat(sig).contains("Service");
    }

    @Test
    void doesNotMatchIfStatement() {
        List<String> lines = List.of(
                "public class Foo {",
                "    public void go() {",
                "        if (OldFlag.isEnabled()) {",   // error line (idx 2)
                "        }",
                "    }",
                "}"
        );
        // Should find 'go()' not 'if(...)'
        String sig = detector.detect(lines, 2);
        assertThat(sig).contains("go");
    }

    @Test
    void doesNotMatchForLoop() {
        List<String> lines = List.of(
                "    public void iterate(List<String> items) {",
                "        for (String item : OldItems.get()) {",  // error line (idx 1)
                "        }",
                "    }"
        );
        String sig = detector.detect(lines, 1);
        assertThat(sig).contains("iterate");
    }

    @Test
    void skipsBlankAndCommentLines() {
        List<String> lines = List.of(
                "    public String format(int value) {",
                "        // TODO: remove old API",
                "        ",
                "        return OldFormatter.format(value);",  // error line (idx 3)
                "    }"
        );
        String sig = detector.detect(lines, 3);
        assertThat(sig).contains("format");
    }

    @Test
    void returnsNullWhenNoMethodFound() {
        List<String> lines = List.of(
                "package com.example;",
                "import java.util.List;"
        );
        assertThat(detector.detect(lines, 1)).isNull();
    }

    @Test
    void worksWhenErrorIsOnTheSignatureLineItself() {
        List<String> lines = List.of(
                "public class Foo {",
                "    public OldReturnType doWork(String x) {"  // error line (idx 1)
        );
        String sig = detector.detect(lines, 1);
        assertThat(sig).contains("doWork");
    }
}
