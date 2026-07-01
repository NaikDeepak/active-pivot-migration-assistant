package com.bank.migration.compiler;

import java.time.Duration;
import java.util.List;

/**
 * Outcome of a single {@code mvn compile} invocation.
 */
public record CompilationResult(
        boolean success,
        List<CompilerError> errors,
        String rawOutput,
        Duration duration
) {

    /** True when there are no errors to fix (same as {@link #success}). */
    public boolean hasNoErrors() {
        return errors.isEmpty();
    }

    /**
     * The first error the migration engine should attempt to fix.
     *
     * @throws IllegalStateException if called on a successful build
     */
    public CompilerError firstError() {
        if (errors.isEmpty()) {
            throw new IllegalStateException("No compiler errors — build was successful");
        }
        return errors.getFirst();
    }
}
