package com.bank.migration.console;

import com.bank.migration.compiler.CompilationResult;
import com.bank.migration.compiler.CompilerError;
import com.bank.migration.compiler.CompilerRunner;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.engine.FixOutcome;
import com.bank.migration.engine.MigrationEngine;
import com.bank.migration.fix.FixHistory;
import com.bank.migration.fix.FixRecord;
import com.bank.migration.jar.JarIndexStore;
import com.bank.migration.jar.JarIndexer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsoleMigrationRunnerTest {

    @TempDir
    Path tempDir;

    private MigrationProperties props;
    private CompilerRunner compilerRunner;
    private JarIndexer jarIndexer;
    private JarIndexStore jarIndexStore;
    private FixHistory fixHistory;
    private DiffPrinter diffPrinter;
    private MigrationEngine migrationEngine;
    private ConsoleMigrationRunner runner;

    @BeforeEach
    void setUp() {
        props = mock(MigrationProperties.class);
        when(props.projectRoot()).thenReturn(tempDir);
        when(props.jarsDir()).thenReturn(tempDir.resolve("jars"));

        compilerRunner   = mock(CompilerRunner.class);
        jarIndexer       = mock(JarIndexer.class);
        jarIndexStore    = mock(JarIndexStore.class);
        fixHistory       = mock(FixHistory.class);
        diffPrinter      = mock(DiffPrinter.class);
        migrationEngine  = mock(MigrationEngine.class);

        runner = new ConsoleMigrationRunner(
                props, compilerRunner, jarIndexer, jarIndexStore,
                fixHistory, diffPrinter, migrationEngine);
    }

    private String run(String input) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        runner.runLoop(new BufferedReader(new StringReader(input)), out);
        return baos.toString();
    }

    private CompilationResult successResult() {
        return new CompilationResult(true, List.of(), "", Duration.ofMillis(42));
    }

    private CompilationResult failureResult(int errorCount) {
        List<CompilerError> errors = new java.util.ArrayList<>();
        for (int i = 0; i < errorCount; i++) {
            errors.add(new CompilerError(
                    Path.of("Foo.java"), i + 1, 5,
                    "cannot find symbol", List.of("symbol: class Missing" + i)));
        }
        return new CompilationResult(false, errors, "raw output", Duration.ofMillis(100));
    }

    @Test
    void quitExitsLoop() throws IOException {
        String output = run("quit\n");
        assertThat(output).contains("Goodbye");
    }

    @Test
    void exitAliasesQuit() throws IOException {
        String output = run("exit\n");
        assertThat(output).contains("Goodbye");
    }

    @Test
    void unknownCommandShowsError() throws IOException {
        String output = run("bogus\nquit\n");
        assertThat(output).contains("Unknown command");
        assertThat(output).contains("bogus");
    }

    @Test
    void helpListsAllCommands() throws IOException {
        String output = run("help\nquit\n");
        for (ConsoleCommand cmd : ConsoleCommand.values()) {
            assertThat(output).contains(cmd.keyword);
        }
    }

    @Test
    void compileSuccessShowsBuildSuccess() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(successResult());

        String output = run("compile\nquit\n");

        assertThat(output).contains("BUILD SUCCESS");
    }

    @Test
    void compileFailureShowsErrorCount() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(3));

        String output = run("compile\nquit\n");

        assertThat(output).contains("BUILD FAILURE");
        assertThat(output).contains("3 error(s)");
    }

    @Test
    void compileFailureShowsFirstFiveErrors() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(7));

        String output = run("compile\nquit\n");

        assertThat(output).contains("2 more");
    }

    @Test
    void compileFailureShowsCurrentError() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));

        String output = run("compile\nquit\n");

        assertThat(output).contains("Current error");
        assertThat(output).contains("cannot find symbol");
    }

    @Test
    void scanWithMissingDirShowsMessage() throws IOException {
        when(props.jarsDir()).thenReturn(Path.of("/nonexistent/jars-dir"));

        String output = run("scan\nquit\n");

        assertThat(output).contains("not found");
    }

    @Test
    void scanWithEmptyDirShowsNoJarsMessage() throws IOException {
        // tempDir exists but has no JARs
        when(props.jarsDir()).thenReturn(tempDir);

        String output = run("scan\nquit\n");

        assertThat(output).contains("No JAR files found");
    }

    @Test
    void nextBeforeCompileShowsMessage() throws IOException {
        String output = run("next\nquit\n");
        assertThat(output).contains("compile");
    }

    @Test
    void nextAfterSuccessfulCompileShowsNoErrors() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(successResult());

        String output = run("compile\nnext\nquit\n");

        assertThat(output).contains("No errors");
    }

    @Test
    void nextNavigatesToNextError() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(2));

        String output = run("compile\nnext\nquit\n");

        // After compile, error index is 0; after next, it's 1
        assertThat(output).contains("2/2");
    }

    @Test
    void statusBeforeCompileShowsNoCompilationYet() throws IOException {
        String output = run("status\nquit\n");
        assertThat(output).contains("No compilation run yet");
    }

    @Test
    void statusAfterSuccessShowsSuccess() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(successResult());

        String output = run("compile\nstatus\nquit\n");

        assertThat(output).contains("SUCCESS");
    }

    @Test
    void historyBeforeCompileShowsMessage() throws IOException {
        String output = run("history\nquit\n");
        assertThat(output).contains("compile");
    }

    @Test
    void historyWithNoSimilarFixesShowsNoFixesMessage() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        when(fixHistory.findSimilar(any(), anyInt())).thenReturn(List.of());

        String output = run("compile\nhistory\nquit\n");

        assertThat(output).contains("No similar fixes");
    }

    @Test
    void historyShowsSimilarFixes() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        FixRecord fix = new FixRecord(
                "Foo.java:1:5:cannot find symbol",
                "/proj/Foo.java",
                "cannot find symbol",
                "symbol: class Missing0",
                "--- a/Foo.java\n+++ b/Foo.java\n@@ -1 +1 @@\n-old\n+new\n",
                "2026-07-01T10:00:00Z",
                true
        );
        when(fixHistory.findSimilar(any(), anyInt())).thenReturn(List.of(fix));

        String output = run("compile\nhistory\nquit\n");

        assertThat(output).contains("1 similar fix");
        assertThat(output).contains("cannot find symbol");
    }

    @Test
    void historyAcceptsTopKArgument() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        when(fixHistory.findSimilar(any(), anyInt())).thenReturn(List.of());

        // Should not throw
        assertThat(run("compile\nhistory 3\nquit\n")).contains("No similar fixes");
    }

    @Test
    void fixBeforeCompileShowsMessage() throws IOException {
        String output = run("fix\nquit\n");
        assertThat(output).contains("compile");
    }

    @Test
    void fixAfterSuccessfulCompileShowsNoErrorsMessage() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(successResult());

        String output = run("compile\nfix\nquit\n");

        assertThat(output).containsIgnoringCase("No errors");
    }

    @Test
    void fixDelegatesSuccessToOutput() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        FixRecord fix = new FixRecord("sig", "/Foo.java", "cannot find symbol", "",
                "@@ -1 +1 @@\n-old\n+new\n", "2026-07-01T10:00:00Z", true);
        when(migrationEngine.fixNextError(any(), any(), any()))
                .thenReturn(new FixOutcome.Success(fix));

        String output = run("compile\nfix\nquit\n");

        assertThat(output).containsIgnoringCase("Fix applied");
    }

    @Test
    void fixDelegatesNeedsContextToOutput() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        when(migrationEngine.fixNextError(any(), any(), any()))
                .thenReturn(new FixOutcome.NeedsContext("Missing JAR index for com.qps"));

        String output = run("compile\nfix\nquit\n");

        assertThat(output).contains("Missing JAR index");
    }

    @Test
    void fixDelegatesRejectedToOutput() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        when(migrationEngine.fixNextError(any(), any(), any()))
                .thenReturn(new FixOutcome.Rejected());

        String output = run("compile\nfix\nquit\n");

        assertThat(output).containsIgnoringCase("rejected");
    }

    @Test
    void fixDelegatesApplyFailedToOutput() throws IOException {
        when(compilerRunner.compile(any())).thenReturn(failureResult(1));
        when(migrationEngine.fixNextError(any(), any(), any()))
                .thenReturn(new FixOutcome.ApplyFailed("line mismatch"));

        String output = run("compile\nfix\nquit\n");

        assertThat(output).contains("Fix failed");
        assertThat(output).contains("line mismatch");
    }

    @Test
    void blankLinesAreIgnored() throws IOException {
        String output = run("\n   \nquit\n");
        assertThat(output).contains("Goodbye");
        assertThat(output).doesNotContain("Unknown command");
    }

    @Test
    void welcomeMessageIsAlwaysShown() throws IOException {
        String output = run("quit\n");
        assertThat(output).contains("ActivePivot Migration Assistant");
        assertThat(output).contains("help");
    }
}
