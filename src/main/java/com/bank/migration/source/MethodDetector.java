package com.bank.migration.source;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Best-effort detector for the Java method or constructor that encloses a given line.
 *
 * <p>Strategy: scan upward from the error line; return the first line that looks like a
 * method/constructor signature (has a {@code (} that is not preceded by a control-flow keyword
 * and not an assignment expression). Returns {@code null} when detection fails.
 *
 * <p>Handles single-line signatures reliably. Multi-line signatures (parameters split across
 * lines) produce a partial match — the signature head is still useful for the AI prompt.
 */
@Component
class MethodDetector {

    // Modifiers recognised as part of a method signature
    private static final Pattern METHOD_SIGNATURE = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|synchronized|" +
            "abstract|native|default|strictfp)\\s+)*" +
            "(?:<[^>()]+>\\s+)?" +          // optional generic type params: <T>
            "[\\w$<>\\[\\]]+(?:\\s*<[^>]*>)?\\s+" +  // return type (inc. generics)
            "[\\w$]+\\s*\\("                // methodName(
    );

    // Lines that contain '(' but are not method declarations
    private static final Pattern CONTROL_FLOW = Pattern.compile(
            "^\\s*(?:if|else\\s+if|for|while|switch|catch|try|return|throw|new|assert|do)\\b"
    );

    /**
     * @param lines     all lines of the source file (0-based)
     * @param errorIdx  0-based index of the error line
     * @return signature string, or {@code null} if not found
     */
    String detect(List<String> lines, int errorIdx) {
        for (int i = Math.min(errorIdx, lines.size() - 1); i >= 0; i--) {
            String line = lines.get(i);
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }
            if (CONTROL_FLOW.matcher(line).find()) {
                continue;
            }
            if (METHOD_SIGNATURE.matcher(line).find()) {
                return trimmed;
            }
        }
        return null;
    }
}
