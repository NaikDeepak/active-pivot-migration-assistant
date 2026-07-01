package com.bank.migration.compiler;

import java.nio.file.Path;

/**
 * Runs a build tool against a project root and returns the result.
 * Phase 2 provides a Maven implementation; the interface makes it swappable.
 */
public interface CompilerRunner {

    CompilationResult compile(Path projectRoot);
}
