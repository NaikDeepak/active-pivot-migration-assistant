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
import com.bank.migration.jar.model.JarIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Component
public class ConsoleMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ConsoleMigrationRunner.class);

    private final MigrationProperties props;
    private final CompilerRunner compilerRunner;
    private final JarIndexer jarIndexer;
    private final JarIndexStore jarIndexStore;
    private final FixHistory fixHistory;
    private final DiffPrinter diffPrinter;
    private final MigrationEngine migrationEngine;

    public ConsoleMigrationRunner(MigrationProperties props,
                                   CompilerRunner compilerRunner,
                                   JarIndexer jarIndexer,
                                   JarIndexStore jarIndexStore,
                                   FixHistory fixHistory,
                                   DiffPrinter diffPrinter,
                                   MigrationEngine migrationEngine) {
        this.props = props;
        this.compilerRunner = compilerRunner;
        this.jarIndexer = jarIndexer;
        this.jarIndexStore = jarIndexStore;
        this.fixHistory = fixHistory;
        this.diffPrinter = diffPrinter;
        this.migrationEngine = migrationEngine;
    }

    @Override
    public void run(String... args) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            runLoop(reader, System.out);
        } catch (IOException e) {
            log.error("Console error: {}", e.getMessage());
        }
    }

    void runLoop(BufferedReader in, PrintStream out) throws IOException {
        SessionState session = new SessionState();
        out.println("=== ActivePivot Migration Assistant ===");
        out.println("Type 'help' for available commands.");
        out.println();

        String line;
        while ((line = in.readLine()) != null) {
            String stripped = line.strip();
            if (stripped.isEmpty()) continue;

            String[] parts = stripped.split("\\s+", 2);
            String cmdWord = parts[0];
            String arg = parts.length > 1 ? parts[1] : "";

            ConsoleCommand command = ConsoleCommand.parse(cmdWord).orElse(null);
            if (command == null) {
                out.println("Unknown command: '" + cmdWord + "'. Type 'help' for commands.");
                out.println();
                continue;
            }

            switch (command) {
                case HELP    -> printHelp(out);
                case QUIT    -> { out.println("Goodbye."); return; }
                case COMPILE -> runCompile(session, out);
                case SCAN    -> runScan(out);
                case NEXT    -> runNext(session, out);
                case HISTORY -> runHistory(session, arg, out);
                case STATUS  -> out.println(session.summary());
                case FIX     -> runFix(session, in, out);
            }
            out.println();
        }
    }

    private void runCompile(SessionState session, PrintStream out) {
        out.println("Compiling " + props.projectRoot() + " ...");
        CompilationResult result = compilerRunner.compile(props.projectRoot());
        session.updateCompilation(result);

        if (result.success()) {
            out.println("BUILD SUCCESS — no errors.");
        } else {
            out.printf("BUILD FAILURE — %d error(s) in %dms%n",
                    result.errors().size(), result.duration().toMillis());
            out.println();
            result.errors().stream().limit(5).forEach(e ->
                    out.printf("  [%s:%d:%d] %s%n",
                            e.sourceFile().getFileName(), e.line(), e.column(), e.message()));
            if (result.errors().size() > 5) {
                out.printf("  ... and %d more. Use 'next' to navigate.%n",
                        result.errors().size() - 5);
            }
            out.println();
            session.currentError().ifPresent(e -> printError(e, out));
        }
    }

    private void runScan(PrintStream out) {
        Path jarsDir = props.jarsDir();
        if (!Files.isDirectory(jarsDir)) {
            out.println("Jars directory not found: " + jarsDir);
            out.println("Create it and place ActivePivot JARs there, then run 'scan' again.");
            return;
        }

        List<Path> jars;
        try (Stream<Path> files = Files.list(jarsDir)) {
            jars = files.filter(p -> p.getFileName().toString().endsWith(".jar")).toList();
        } catch (IOException e) {
            out.println("Cannot list jars directory: " + e.getMessage());
            return;
        }

        if (jars.isEmpty()) {
            out.println("No JAR files found in " + jarsDir);
            return;
        }

        out.printf("Found %d JAR(s). Indexing...%n", jars.size());
        int indexed = 0, skipped = 0;
        for (Path jar : jars) {
            if (jarIndexStore.isAlreadyIndexed(jar)) {
                out.println("  [SKIP]  " + jar.getFileName() + " (already indexed)");
                skipped++;
            } else {
                out.println("  [INDEX] " + jar.getFileName() + " ...");
                try {
                    JarIndex idx = jarIndexer.index(jar);
                    jarIndexStore.save(idx);
                    out.printf("          → %d classes indexed%n", idx.classes().size());
                    indexed++;
                } catch (Exception e) {
                    out.println("          ! Failed: " + e.getMessage());
                    log.warn("Failed to index {}: {}", jar.getFileName(), e.getMessage());
                }
            }
        }
        out.printf("Done: %d indexed, %d skipped.%n", indexed, skipped);
    }

    private void runNext(SessionState session, PrintStream out) {
        if (!session.hasCompiled()) {
            out.println("Run 'compile' first.");
            return;
        }
        if (!session.hasErrors()) {
            out.println("No errors to navigate.");
            return;
        }
        session.nextError();
        out.printf("Error %d/%d%n", session.currentErrorIndex() + 1, session.totalErrors());
        session.currentError().ifPresent(e -> printError(e, out));
    }

    private void runFix(SessionState session, BufferedReader in, PrintStream out) throws IOException {
        if (!session.hasCompiled()) {
            out.println("Run 'compile' first.");
            return;
        }
        if (!session.hasErrors()) {
            out.println("No errors to fix. Last compile was successful.");
            return;
        }

        FixOutcome outcome = migrationEngine.fixNextError(session, in, out);
        switch (outcome) {
            case FixOutcome.Success s ->
                    out.printf("Fix applied and recorded%s.%n",
                            s.fix().compilationSucceeded() ? " (compile passed)" : " (compile still failing)");
            case FixOutcome.NeedsContext nc -> {
                out.println("AI needs additional context:");
                out.println("  " + nc.request());
                out.println("Add the requested information to the knowledge directory and retry.");
            }
            case FixOutcome.Rejected r    -> out.println("Fix rejected — no changes made.");
            case FixOutcome.ApplyFailed a -> out.println("Fix failed: " + a.reason());
            case FixOutcome.NoError ne    -> out.println("No current error to fix.");
        }
    }

    private void runHistory(SessionState session, String arg, PrintStream out) {
        int topK = 5;
        if (!arg.isBlank()) {
            try { topK = Integer.parseInt(arg.strip()); } catch (NumberFormatException ignored) {}
        }

        if (!session.hasCompiled()) {
            out.println("Run 'compile' first.");
            return;
        }
        CompilerError error = session.currentError().orElse(null);
        if (error == null) {
            out.println("No current error.");
            return;
        }

        List<FixRecord> fixes = fixHistory.findSimilar(error, topK);
        if (fixes.isEmpty()) {
            out.println("No similar fixes found in history.");
            return;
        }

        out.printf("Found %d similar fix(es):%n", fixes.size());
        for (int i = 0; i < fixes.size(); i++) {
            FixRecord f = fixes.get(i);
            out.printf("%n[%d] %s%n    Applied: %s | Succeeded: %s%n",
                    i + 1, f.errorMessage(), f.appliedAt(), f.compilationSucceeded());
            diffPrinter.print(f.diffPatch(), out);
        }
    }

    private void printError(CompilerError e, PrintStream out) {
        out.println("Current error:");
        out.printf("  File  : %s%n", e.sourceFile());
        out.printf("  Line  : %d, Col: %d%n", e.line(), e.column());
        out.printf("  Error : %s%n", e.message());
        if (!e.details().isEmpty()) {
            e.details().forEach(d -> out.println("          " + d));
        }
    }

    private void printHelp(PrintStream out) {
        out.println("Available commands:");
        for (ConsoleCommand cmd : ConsoleCommand.values()) {
            out.printf("  %-10s  %s%n", cmd.keyword, cmd.description);
        }
    }
}
