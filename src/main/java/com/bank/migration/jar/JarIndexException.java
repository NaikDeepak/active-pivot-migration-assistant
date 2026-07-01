package com.bank.migration.jar;

/**
 * Thrown when a JAR file cannot be opened, read, or its index cannot be persisted.
 */
public class JarIndexException extends RuntimeException {

    public JarIndexException(String message, Throwable cause) {
        super(message, cause);
    }
}
