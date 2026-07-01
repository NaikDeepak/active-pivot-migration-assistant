package com.bank.migration.knowledge;

/**
 * Thrown when a knowledge document cannot be read or parsed.
 */
public class KnowledgeLoadException extends RuntimeException {

    public KnowledgeLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
