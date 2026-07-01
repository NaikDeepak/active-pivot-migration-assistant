package com.bank.migration.prompt;

/**
 * A ready-to-send AI prompt, split into system and user parts.
 *
 * <p>The {@code systemPrompt} carries the role and rules (sent once per conversation).
 * The {@code userPrompt} carries the per-error context (sent once per error).
 * {@code estimatedTokens} is the sum of both, used by the AI client to enforce limits.
 */
public record MigrationPrompt(
        String systemPrompt,
        String userPrompt,
        int estimatedTokens
) {

    /** Combined text for providers that use a single-string API. */
    public String combined() {
        return systemPrompt + "\n\n" + userPrompt;
    }
}
