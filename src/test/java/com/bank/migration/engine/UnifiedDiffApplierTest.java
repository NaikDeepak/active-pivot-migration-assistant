package com.bank.migration.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UnifiedDiffApplierTest {

    @TempDir
    Path tempDir;

    private final UnifiedDiffApplier applier = new UnifiedDiffApplier();

    private Path writeSource(String... lines) throws IOException {
        Path file = tempDir.resolve("Foo.java");
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
        return file;
    }

    private List<String> readLines(Path file) throws IOException {
        return Files.readAllLines(file, StandardCharsets.UTF_8);
    }

    @Test
    void appliesSimpleLineReplacement() throws IOException {
        Path file = writeSource(
                "import old.Pkg;",
                "public class Foo {",
                "    Old obj = new Old();",
                "}");

        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -3,1 +3,1 @@
                -    Old obj = new Old();
                +    New obj = new New();
                """;

        ApplyResult result = applier.apply(file, diff);

        assertThat(result.success()).isTrue();
        assertThat(readLines(file).get(2)).isEqualTo("    New obj = new New();");
    }

    @Test
    void appliesAddition() throws IOException {
        Path file = writeSource(
                "public class Foo {",
                "}");

        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,2 +1,3 @@
                 public class Foo {
                +    void method() {}
                 }
                """;

        ApplyResult result = applier.apply(file, diff);

        assertThat(result.success()).isTrue();
        List<String> lines = readLines(file);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).isEqualTo("    void method() {}");
    }

    @Test
    void appliesDeletion() throws IOException {
        Path file = writeSource(
                "public class Foo {",
                "    // TODO remove me",
                "    void method() {}",
                "}");

        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,4 +1,3 @@
                 public class Foo {
                -    // TODO remove me
                     void method() {}
                 }
                """;

        ApplyResult result = applier.apply(file, diff);

        assertThat(result.success()).isTrue();
        assertThat(readLines(file)).hasSize(3);
        assertThat(readLines(file)).doesNotContain("    // TODO remove me");
    }

    @Test
    void appliesMultipleHunks() throws IOException {
        Path file = writeSource(
                "import old.A;",
                "import old.B;",
                "public class Foo {",
                "    A a;",
                "    B b;",
                "}");

        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,1 +1,1 @@
                -import old.A;
                +import new.A;
                @@ -4,1 +4,1 @@
                -    A a;
                +    A a; // migrated
                """;

        ApplyResult result = applier.apply(file, diff);

        assertThat(result.success()).isTrue();
        List<String> lines = readLines(file);
        assertThat(lines.get(0)).isEqualTo("import new.A;");
        assertThat(lines.get(3)).isEqualTo("    A a; // migrated");
    }

    @Test
    void appliesHunkWithContextLines() throws IOException {
        Path file = writeSource(
                "line1",
                "line2",
                "OLD",
                "line4",
                "line5");

        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -2,3 +2,3 @@
                 line2
                -OLD
                +NEW
                 line4
                """;

        ApplyResult result = applier.apply(file, diff);

        assertThat(result.success()).isTrue();
        assertThat(readLines(file).get(2)).isEqualTo("NEW");
    }

    @Test
    void failsWhenSourceFileDoesNotExist() {
        ApplyResult result = applier.apply(Path.of("/nonexistent/Foo.java"),
                "@@ -1 +1 @@\n-old\n+new\n");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("not found");
    }

    @Test
    void failsWhenNoDiffHunksFound() throws IOException {
        Path file = writeSource("class Foo {}");

        ApplyResult result = applier.apply(file, "--- a/Foo.java\n+++ b/Foo.java\n");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("no hunks");
    }

    @Test
    void handlesTrailingCarriageReturns() throws IOException {
        Path file = writeSource("old line");

        String diff = "@@ -1,1 +1,1 @@\r\n-old line\r\n+new line\r\n";

        ApplyResult result = applier.apply(file, diff);

        assertThat(result.success()).isTrue();
        assertThat(readLines(file).get(0)).isEqualTo("new line");
    }

    @Test
    void returnsSuccessMessageWithHunkCount() throws IOException {
        Path file = writeSource("old");

        ApplyResult result = applier.apply(file, "@@ -1 +1 @@\n-old\n+new\n");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("1 hunk");
    }
}
