package com.bank.migration.knowledge;

import com.bank.migration.compiler.CompilerError;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts meaningful search terms from a {@link CompilerError} for knowledge-section lookup.
 *
 * <p>Strategy:
 * <ol>
 *   <li>Extract all Java identifiers from message and details.</li>
 *   <li>Split camelCase/PascalCase identifiers into their component words.</li>
 *   <li>Remove terms shorter than 3 chars and well-known stop-words that appear in every error.</li>
 * </ol>
 */
@Component
class TermExtractor {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_$]*");

    // Splits on camelCase and PascalCase boundaries, e.g. "ActiveMonitor" → ["Active","Monitor"]
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"
    );

    private static final Set<String> STOP_WORDS = Set.of(
            // javac boilerplate that appears in every error
            "cannot", "find", "symbol", "class", "method", "variable",
            "package", "does", "not", "exist", "incompatible", "types",
            "location", "constructor", "interface", "annotation",
            // short Java keywords that slip through the length filter
            "the", "and", "for", "with", "that", "this", "from",
            // Maven output noise
            "error", "errors", "warning", "warnings", "note", "notes"
    );

    Set<String> extract(CompilerError error) {
        Set<String> terms = new LinkedHashSet<>();

        // Prioritise the symbol: line in details — most specific to the missing API
        error.details().stream()
                .filter(d -> d.stripLeading().startsWith("symbol"))
                .forEach(d -> addIdentifiers(terms, afterColon(d)));

        // All details provide secondary context
        error.details().forEach(d -> addIdentifiers(terms, d));

        // The message itself (e.g., "cannot find symbol" is noise, but method names inside it are not)
        addIdentifiers(terms, error.message());

        return terms.stream()
                .filter(t -> t.length() >= 3)
                .filter(t -> !STOP_WORDS.contains(t.toLowerCase()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void addIdentifiers(Set<String> terms, String text) {
        Matcher m = IDENTIFIER.matcher(text);
        while (m.find()) {
            String id = m.group();
            terms.add(id);
            // Split PascalCase/camelCase and add parts too
            for (String part : CAMEL_BOUNDARY.split(id)) {
                if (part.length() >= 3) {
                    terms.add(part);
                }
            }
        }
    }

    private String afterColon(String detail) {
        int idx = detail.indexOf(':');
        return idx >= 0 ? detail.substring(idx + 1) : detail;
    }
}
