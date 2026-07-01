package com.bank.migration.ai;

/**
 * The parsed result of one AI provider call.
 *
 * <p>When {@code needsContext} is {@code true}, the provider could not produce a fix and
 * the migration engine should surface {@code contextRequest} to the user instead of applying
 * a diff.
 */
public record AiResponse(
        String rawText,
        boolean needsContext,
        String contextRequest,  // non-null when needsContext == true
        String diffPatch,       // non-null when needsContext == false
        String providerName,
        int inputTokensUsed,
        int outputTokensUsed
) {

    public static final String NEED_CONTEXT_PREFIX = "NEED_CONTEXT:";

    /** Parses the raw AI response text into a structured {@link AiResponse}. */
    public static AiResponse parse(String rawText, String providerName,
                                   int inputTokens, int outputTokens) {
        String trimmed = rawText == null ? "" : rawText.strip();

        if (trimmed.startsWith(NEED_CONTEXT_PREFIX)) {
            String request = trimmed.substring(NEED_CONTEXT_PREFIX.length()).strip();
            return new AiResponse(rawText, true, request, null,
                    providerName, inputTokens, outputTokens);
        }

        String diff = extractDiff(trimmed);
        return new AiResponse(rawText, false, null, diff,
                providerName, inputTokens, outputTokens);
    }

    private static String extractDiff(String text) {
        String[] lines = text.split("\\r?\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.startsWith("---") || l.startsWith("+++") || l.startsWith("@@")) {
                StringBuilder diff = new StringBuilder();
                for (int j = i; j < lines.length; j++) {
                    diff.append(lines[j]).append("\n");
                }
                return diff.toString().strip();
            }
        }
        return text;  // return as-is if no diff markers — let the engine decide
    }
}
