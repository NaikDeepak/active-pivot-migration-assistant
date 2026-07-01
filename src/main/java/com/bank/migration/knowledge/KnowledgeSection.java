package com.bank.migration.knowledge;

import java.nio.file.Path;

/**
 * A single logical section extracted from a migration knowledge document.
 *
 * <p>For Markdown files a section is a heading + its following body.
 * For plain-text files a section is a paragraph block.
 * The {@code sectionIndex} is the ordinal within the source file, used for stable ordering.
 */
public record KnowledgeSection(
        Path sourceFile,
        String heading,      // null for preamble text before the first heading
        String body,
        int sectionIndex
) {

    /** Combined text used for keyword matching. */
    public String fullText() {
        return (heading != null ? heading + "\n" : "") + body;
    }

    /** Short label for log messages and console display. */
    public String label() {
        String file = sourceFile.getFileName().toString();
        return heading != null ? file + " § " + heading.strip() : file + " (preamble)";
    }
}
