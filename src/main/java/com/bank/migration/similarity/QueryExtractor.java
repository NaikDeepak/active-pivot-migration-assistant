package com.bank.migration.similarity;

import com.bank.migration.compiler.CompilerError;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the structured detail lines of a {@link CompilerError} into a {@link SearchQuery}.
 *
 * <p>Handled error shapes:
 * <ul>
 *   <li>{@code symbol: class X}          → class search</li>
 *   <li>{@code symbol: method X(...)}     → method search</li>
 *   <li>{@code symbol: variable/field X}  → treated as class search on the field type</li>
 *   <li>{@code package X does not exist}  → package search</li>
 * </ul>
 */
@Component
class QueryExtractor {

    private static final Pattern CLASS_SYMBOL = Pattern.compile(
            "symbol\\s*:\\s*class\\s+(\\S+)"
    );
    private static final Pattern METHOD_SYMBOL = Pattern.compile(
            "symbol\\s*:\\s*method\\s+(\\w+)\\s*\\(([^)]*?)\\)"
    );
    private static final Pattern VARIABLE_SYMBOL = Pattern.compile(
            "symbol\\s*:\\s*(?:variable|field)\\s+(\\S+)"
    );
    private static final Pattern PACKAGE_MISSING = Pattern.compile(
            "package\\s+([\\w.]+)\\s+does\\s+not\\s+exist"
    );
    private static final Pattern LOCATION = Pattern.compile(
            "location\\s*:\\s*(?:class|interface|enum)\\s+(\\S+)"
    );

    SearchQuery extract(CompilerError error) {
        String details  = String.join("\n", error.details());
        String message  = error.message();

        // ── Class ──────────────────────────────────────────────────────────
        String missingClass = match(CLASS_SYMBOL, details);

        // ── Method ────────────────────────────────────────────────────────
        String missingMethod = null;
        List<String> methodParams = List.of();
        Matcher mm = METHOD_SYMBOL.matcher(details);
        if (mm.find()) {
            missingMethod = mm.group(1);
            String paramStr = mm.group(2).trim();
            methodParams = paramStr.isEmpty()
                    ? List.of()
                    : Arrays.asList(paramStr.split("\\s*,\\s*"));
        }

        // ── Package ───────────────────────────────────────────────────────
        String missingPackage = match(PACKAGE_MISSING, message);
        // Also check if message itself is a class import failure
        if (missingClass == null && missingPackage != null) {
            // "package com.qfs.monitoring does not exist" → last segment might be a class
            String[] parts = missingPackage.split("\\.");
            String last = parts[parts.length - 1];
            if (Character.isUpperCase(last.charAt(0))) {
                // Looks like a class name was imported directly
                missingClass  = last;
                missingPackage = missingPackage.substring(0, missingPackage.lastIndexOf('.'));
            }
        }

        // ── Location ──────────────────────────────────────────────────────
        String location = match(LOCATION, details);

        return new SearchQuery(missingClass, missingMethod, methodParams, missingPackage, location);
    }

    private String match(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
