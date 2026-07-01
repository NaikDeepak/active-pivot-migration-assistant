package com.bank.migration.compiler;

import com.bank.migration.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes {@code mvn <pre-goals> compile} in the target project and returns a
 * structured {@link CompilationResult}.
 */
@Component
public class MavenCompilerRunner implements CompilerRunner {

    private static final Logger log = LoggerFactory.getLogger(MavenCompilerRunner.class);

    private final MigrationProperties props;
    private final MavenOutputParser parser;
    private final ProcessRunner processRunner;

    public MavenCompilerRunner(
            MigrationProperties props,
            MavenOutputParser parser,
            ProcessRunner processRunner) {
        this.props = props;
        this.parser = parser;
        this.processRunner = processRunner;
    }

    @Override
    public CompilationResult compile(Path projectRoot) {
        validateProjectRoot(projectRoot);

        List<String> command = buildCommand();
        log.info("Compiling {} with: {}", projectRoot, String.join(" ", command));

        Instant start = Instant.now();
        try {
            ProcessRunner.ProcessOutput output = processRunner.run(command, projectRoot);
            Duration elapsed = Duration.between(start, Instant.now());

            boolean success = output.succeeded();
            List<CompilerError> errors = success ? List.of() : parser.parse(output.stdout());

            log.info("Build {} — {}ms, {} error(s)",
                    success ? "SUCCESS" : "FAILURE", elapsed.toMillis(), errors.size());

            return new CompilationResult(success, errors, output.stdout(), elapsed);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new CompilerException(
                    "Failed to launch Maven process in: " + projectRoot, e);
        }
    }

    private void validateProjectRoot(Path projectRoot) {
        if (!Files.isDirectory(projectRoot)) {
            throw new CompilerException(
                    "Project root does not exist or is not a directory: " + projectRoot, null);
        }
        if (!Files.exists(projectRoot.resolve("pom.xml"))) {
            throw new CompilerException(
                    "No pom.xml found in: " + projectRoot, null);
        }
    }

    private List<String> buildCommand() {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.mavenExecutable());
        cmd.addAll(props.mavenPreGoals());
        cmd.add("compile");
        return List.copyOf(cmd);
    }
}
