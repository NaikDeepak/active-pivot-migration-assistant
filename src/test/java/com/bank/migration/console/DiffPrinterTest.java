package com.bank.migration.console;

import com.bank.migration.config.MigrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiffPrinterTest {

    private DiffPrinter printer;
    private ByteArrayOutputStream out;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
    }

    private DiffPrinter printerWith(boolean color) {
        MigrationProperties props = mock(MigrationProperties.class);
        MigrationProperties.ConsoleProperties console = mock(MigrationProperties.ConsoleProperties.class);
        when(props.console()).thenReturn(console);
        when(console.colorDiff()).thenReturn(color);
        return new DiffPrinter(props);
    }

    private String run(String diff, boolean color) {
        printerWith(color).print(diff, new PrintStream(out));
        return out.toString();
    }

    @Test
    void nullDiffShowsNoDiff() {
        assertThat(run(null, false)).contains("(no diff)");
    }

    @Test
    void blankDiffShowsNoDiff() {
        assertThat(run("   ", false)).contains("(no diff)");
    }

    @Test
    void plainModePrintsLinesWithoutAnsi() {
        String diff = "--- a/Foo.java\n+++ b/Foo.java\n@@ -1 +1 @@\n-old\n+new\n";
        String output = run(diff, false);

        assertThat(output).contains("--- a/Foo.java");
        assertThat(output).contains("+++ b/Foo.java");
        assertThat(output).contains("-old");
        assertThat(output).contains("+new");
        assertThat(output).doesNotContain("[3"); // no ANSI escape codes
    }

    @Test
    void colorModeWrapsAddedLinesInGreen() {
        String output = run("+added line\n", true);
        assertThat(output).contains("[32m");  // green start
        assertThat(output).contains("[0m");   // reset
        assertThat(output).contains("+added line");
    }

    @Test
    void colorModeWrapsRemovedLinesInRed() {
        String output = run("-removed line\n", true);
        assertThat(output).contains("[31m");  // red start
        assertThat(output).contains("-removed line");
    }

    @Test
    void colorModeWrapsHunkHeaderInCyan() {
        String output = run("@@ -1,3 +1,3 @@\n", true);
        assertThat(output).contains("[36m");  // cyan start
        assertThat(output).contains("@@ -1,3 +1,3 @@");
    }

    @Test
    void fileHeaderLinesAreNotColored() {
        // "---" and "+++" file headers should not be colored as add/remove
        String output = run("--- a/Foo.java\n+++ b/Foo.java\n", true);
        // Should not start with red/green before "---"/"+++"
        assertThat(output).doesNotContain("[31m--- ");
        assertThat(output).doesNotContain("[32m+++ ");
    }
}
