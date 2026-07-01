package com.bank.migration.compiler;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Production {@link ProcessRunner} that delegates to the OS via {@link ProcessBuilder}.
 * stderr is merged into stdout so callers get one combined stream.
 */
@Component
class SystemProcessRunner implements ProcessRunner {

    @Override
    public ProcessOutput run(List<String> command, Path workingDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(workingDir.toFile())
                .redirectErrorStream(true);

        Process process = pb.start();
        String stdout = IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        return new ProcessOutput(exitCode, stdout);
    }
}
