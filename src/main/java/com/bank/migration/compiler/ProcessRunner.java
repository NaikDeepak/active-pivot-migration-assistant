package com.bank.migration.compiler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Thin abstraction over OS process execution.
 * Extracted so {@link MavenCompilerRunner} can be unit-tested without spawning a real process.
 */
@FunctionalInterface
public interface ProcessRunner {

    ProcessOutput run(List<String> command, Path workingDir) throws IOException, InterruptedException;

    record ProcessOutput(int exitCode, String stdout) {
        public boolean succeeded() {
            return exitCode == 0;
        }
    }
}
