package com.bank.migration.compiler;

/**
 * Thrown when the compiler process cannot be started or is interrupted.
 * Distinct from a build failure (which is a normal {@link CompilationResult}).
 */
public class CompilerException extends RuntimeException {

    public CompilerException(String message, Throwable cause) {
        super(message, cause);
    }
}
