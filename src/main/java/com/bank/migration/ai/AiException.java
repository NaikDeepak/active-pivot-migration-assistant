package com.bank.migration.ai;

/**
 * Thrown when an AI provider call fails at the transport or protocol level
 * (network error, bad HTTP status, malformed response).
 * Distinct from a valid "NEED_CONTEXT" response, which is represented in {@link AiResponse}.
 */
public class AiException extends RuntimeException {

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiException(String message) {
        super(message);
    }
}
