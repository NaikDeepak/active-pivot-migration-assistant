package com.bank.migration.ai;

import java.io.IOException;

/**
 * Thin abstraction over the raw HTTPS call to the Anthropic Messages API.
 * Extracted as a functional interface so {@link ClaudeAiProvider} is unit-testable
 * without making real network calls.
 */
@FunctionalInterface
public interface ClaudeGateway {

    GatewayResponse send(String jsonBody) throws IOException, InterruptedException;

    record GatewayResponse(int statusCode, String body) {
        public boolean isSuccess() { return statusCode == 200; }
    }
}
