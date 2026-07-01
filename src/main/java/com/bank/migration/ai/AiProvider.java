package com.bank.migration.ai;

import com.bank.migration.prompt.MigrationPrompt;

/**
 * Sends a {@link MigrationPrompt} to an AI model and returns the parsed response.
 * Implementations: {@link MockAiProvider} (testing) and {@link ClaudeAiProvider} (production).
 */
public interface AiProvider {

    AiResponse complete(MigrationPrompt prompt);

    /** Human-readable name used in logs and the console display. */
    String providerName();
}
