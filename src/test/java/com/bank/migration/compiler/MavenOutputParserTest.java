package com.bank.migration.compiler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenOutputParserTest {

    private final MavenOutputParser parser = new MavenOutputParser();

    @Test
    void parsesSingleErrorWithDetails() {
        String output = """
                [INFO] Scanning for projects...
                [ERROR] COMPILATION ERROR :
                [INFO] -------------------------------------------------------------
                [ERROR] /project/src/main/java/com/example/Foo.java:[42,15] cannot find symbol
                  symbol:   class OldActiveMonitor
                  location: class com.example.Foo
                [INFO] 1 error
                """;

        List<CompilerError> errors = parser.parse(output);

        assertThat(errors).hasSize(1);

        CompilerError e = errors.getFirst();
        assertThat(e.sourceFile().getFileName().toString()).isEqualTo("Foo.java");
        assertThat(e.line()).isEqualTo(42);
        assertThat(e.column()).isEqualTo(15);
        assertThat(e.message()).isEqualTo("cannot find symbol");
        assertThat(e.details()).containsExactly(
                "symbol:   class OldActiveMonitor",
                "location: class com.example.Foo"
        );
    }

    @Test
    void parsesMultipleErrorsIndependently() {
        String output = """
                [ERROR] /project/src/main/java/Foo.java:[10,5] cannot find symbol
                  symbol:   class OldClass
                [ERROR] /project/src/main/java/Bar.java:[20,3] package com.old does not exist
                """;

        List<CompilerError> errors = parser.parse(output);

        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).line()).isEqualTo(10);
        assertThat(errors.get(0).details()).containsExactly("symbol:   class OldClass");
        assertThat(errors.get(1).line()).isEqualTo(20);
        assertThat(errors.get(1).details()).isEmpty();
    }

    @Test
    void returnsEmptyListWhenBuildSucceeds() {
        String output = """
                [INFO] --- compiler:3.14.0:compile ---
                [INFO] BUILD SUCCESS
                """;

        assertThat(parser.parse(output)).isEmpty();
    }

    @Test
    void ignoresNonJavaErrorLines() {
        String output = """
                [ERROR] Some Maven lifecycle error, not a compiler error
                [ERROR] /project/src/main/java/Baz.java:[5,1] ';' expected
                """;

        List<CompilerError> errors = parser.parse(output);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().message()).isEqualTo("';' expected");
    }

    @Test
    void handlesContinuationWithCaretPointer() {
        String output = """
                [ERROR] /p/Foo.java:[3,10] incompatible types: int cannot be converted to String
                          int x = 5
                                  ^
                """;

        List<CompilerError> errors = parser.parse(output);

        assertThat(errors).hasSize(1);
        // Caret lines are captured as details — useful for display but not required for fixing
        assertThat(errors.getFirst().details()).isNotEmpty();
    }

    @Test
    void handlesWindowsLineEndings() {
        String output = "[ERROR] /p/Foo.java:[1,1] cannot find symbol\r\n  symbol:   class X\r\n";

        List<CompilerError> errors = parser.parse(output);

        assertThat(errors).hasSize(1);
        assertThat(errors.getFirst().details()).containsExactly("symbol:   class X");
    }

    @Test
    void signatureIsStableAcrossTwoParsesOfSameError() {
        String line = "[ERROR] /p/Foo.java:[10,5] cannot find symbol\n";

        CompilerError a = parser.parse(line).getFirst();
        CompilerError b = parser.parse(line).getFirst();

        assertThat(a.signature()).isEqualTo(b.signature());
    }

    @Test
    void returnsImmutableErrorList() {
        String output = "[ERROR] /p/Foo.java:[1,1] error\n";
        List<CompilerError> errors = parser.parse(output);
        assertThat(errors).isUnmodifiable();
    }
}
