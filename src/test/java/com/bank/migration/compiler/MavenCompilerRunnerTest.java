package com.bank.migration.compiler;

import com.bank.migration.config.MigrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MavenCompilerRunnerTest {

    @Mock
    private MigrationProperties props;

    @Mock
    private ProcessRunner processRunner;

    private MavenCompilerRunner runner;
    private final MavenOutputParser parser = new MavenOutputParser();

    @TempDir
    Path projectRoot;

    @BeforeEach
    void setUp() throws Exception {
        when(props.mavenExecutable()).thenReturn("mvn");
        when(props.mavenPreGoals()).thenReturn(List.of("clean"));
        Files.writeString(projectRoot.resolve("pom.xml"), "<project/>");
        runner = new MavenCompilerRunner(props, parser, processRunner);
    }

    @Test
    void returnsSuccessWhenExitCodeIsZero() throws Exception {
        when(processRunner.run(any(), any()))
                .thenReturn(new ProcessRunner.ProcessOutput(0, "[INFO] BUILD SUCCESS\n"));

        CompilationResult result = runner.compile(projectRoot);

        assertThat(result.success()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.duration()).isPositive();
    }

    @Test
    void extractsErrorsWhenBuildFails() throws Exception {
        String output = """
                [ERROR] /proj/src/Foo.java:[5,3] cannot find symbol
                  symbol:   class OldPivot
                """;
        when(processRunner.run(any(), any()))
                .thenReturn(new ProcessRunner.ProcessOutput(1, output));

        CompilationResult result = runner.compile(projectRoot);

        assertThat(result.success()).isFalse();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.firstError().message()).isEqualTo("cannot find symbol");
    }

    @Test
    void passesCorrectCommandToProcessRunner() throws Exception {
        when(processRunner.run(any(), any()))
                .thenReturn(new ProcessRunner.ProcessOutput(0, ""));

        runner.compile(projectRoot);

        verify(processRunner).run(eq(List.of("mvn", "clean", "compile")), eq(projectRoot));
    }

    @Test
    void rawOutputIsPreservedInResult() throws Exception {
        String rawOutput = "[INFO] BUILD SUCCESS\n";
        when(processRunner.run(any(), any()))
                .thenReturn(new ProcessRunner.ProcessOutput(0, rawOutput));

        CompilationResult result = runner.compile(projectRoot);

        assertThat(result.rawOutput()).isEqualTo(rawOutput);
    }

    @Test
    void rejectsNonExistentProjectRoot() {
        Path missing = Path.of("/nonexistent/project");

        assertThatThrownBy(() -> runner.compile(missing))
                .isInstanceOf(CompilerException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void rejectsProjectRootWithoutPomXml(@TempDir Path emptyDir) {
        assertThatThrownBy(() -> runner.compile(emptyDir))
                .isInstanceOf(CompilerException.class)
                .hasMessageContaining("pom.xml");
    }

    @Test
    void wrapsIoExceptionInCompilerException() throws Exception {
        when(processRunner.run(any(), any()))
                .thenThrow(new IOException("Process not found"));

        assertThatThrownBy(() -> runner.compile(projectRoot))
                .isInstanceOf(CompilerException.class)
                .hasMessageContaining("Failed to launch Maven");
    }

    @Test
    void firstErrorThrowsOnSuccessfulBuild() throws Exception {
        when(processRunner.run(any(), any()))
                .thenReturn(new ProcessRunner.ProcessOutput(0, "[INFO] BUILD SUCCESS\n"));

        CompilationResult result = runner.compile(projectRoot);

        assertThatThrownBy(result::firstError)
                .isInstanceOf(IllegalStateException.class);
    }
}
