package com.bank.migration.source;

/**
 * Thrown when a source file cannot be read or is structurally unreadable.
 */
public class SourceLoadException extends RuntimeException {

    public SourceLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
