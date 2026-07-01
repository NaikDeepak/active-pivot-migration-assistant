package com.bank.migration.source;

import java.nio.file.Path;
import java.util.List;

/**
 * The minimum source context the AI needs to fix a single compiler error.
 *
 * <ul>
 *   <li>{@code imports}          — every import line in the file (signals old API dependencies)</li>
 *   <li>{@code classDeclaration} — the class/interface declaration line (for class-level context)</li>
 *   <li>{@code methodSignature}  — best-effort detection of the enclosing method (may be null)</li>
 *   <li>{@code lines}            — ±N lines around the error (configurable via migration.source-surrounding-lines)</li>
 * </ul>
 */
public record SourceSnippet(
        Path sourceFile,
        List<String> imports,
        String classDeclaration,
        String methodSignature,
        List<NumberedLine> lines,
        int errorLine
) {

    /** Compact, prompt-ready text representation. Marks the error line with {@code <<< ERROR}. */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== Source context ===\n");
        sb.append("File   : ").append(sourceFile).append("\n");

        if (classDeclaration != null) {
            sb.append("Class  : ").append(classDeclaration.strip()).append("\n");
        }
        if (methodSignature != null) {
            sb.append("Method : ").append(methodSignature.strip()).append("\n");
        }

        if (!imports.isEmpty()) {
            sb.append("\nImports:\n");
            imports.forEach(i -> sb.append("  ").append(i.strip()).append("\n"));
        }

        if (!lines.isEmpty()) {
            sb.append("\nCode (lines ")
              .append(lines.getFirst().number())
              .append(" – ")
              .append(lines.getLast().number())
              .append("):\n");

            lines.forEach(l -> {
                String marker = (l.number() == errorLine) ? "  <<< ERROR" : "";
                sb.append(l.toDisplayString()).append(marker).append("\n");
            });
        }

        return sb.toString();
    }
}
