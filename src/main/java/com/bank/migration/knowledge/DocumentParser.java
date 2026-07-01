package com.bank.migration.knowledge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a migration knowledge file into a list of {@link KnowledgeSection}s.
 *
 * <p><b>Markdown</b> ({@code .md}, {@code .markdown}): sections are split on heading lines
 * (lines starting with {@code #}). The preamble before the first heading is a section with
 * {@code heading=null}. Subsections (H3+) are treated as independent sections.
 *
 * <p><b>Plain text</b> ({@code .txt}): sections are paragraph blocks, separated by one or more
 * blank lines. The first line of each block is used as the heading when it is short enough to be
 * a title (≤ 80 chars and not ending with a sentence-terminating character).
 */
@Component
class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    List<KnowledgeSection> parse(Path file) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            throw new KnowledgeLoadException("Cannot read knowledge file: " + file, e);
        }

        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return parseMarkdown(file, content);
        }
        return parsePlainText(file, content);
    }

    // ── Markdown ──────────────────────────────────────────────────────────────

    private List<KnowledgeSection> parseMarkdown(Path file, String content) {
        List<KnowledgeSection> sections = new ArrayList<>();
        String[] lines = content.split("\\r?\\n", -1);

        String currentHeading = null;
        List<String> bodyLines = new ArrayList<>();
        int sectionIdx = 0;

        for (String line : lines) {
            if (line.startsWith("#")) {
                // Flush previous section
                String body = String.join("\n", bodyLines).strip();
                if (!body.isEmpty() || currentHeading != null) {
                    sections.add(new KnowledgeSection(file, currentHeading, body, sectionIdx++));
                }
                currentHeading = line.replaceAll("^#+\\s*", "").strip();
                bodyLines = new ArrayList<>();
            } else {
                bodyLines.add(line);
            }
        }

        // Flush last section
        String body = String.join("\n", bodyLines).strip();
        if (!body.isEmpty() || currentHeading != null) {
            sections.add(new KnowledgeSection(file, currentHeading, body, sectionIdx));
        }

        log.debug("Parsed {} Markdown sections from {}", sections.size(), file.getFileName());
        return List.copyOf(sections);
    }

    // ── Plain text ────────────────────────────────────────────────────────────

    private List<KnowledgeSection> parsePlainText(Path file, String content) {
        List<KnowledgeSection> sections = new ArrayList<>();
        // Split on one or more blank lines
        String[] blocks = content.split("(\\r?\\n){2,}");

        int sectionIdx = 0;
        for (String block : blocks) {
            String trimmed = block.strip();
            if (trimmed.isEmpty()) continue;

            String[] blockLines = trimmed.split("\\r?\\n", 2);
            String firstLine = blockLines[0].strip();
            boolean firstLineIsTitle = firstLine.length() <= 80
                    && !firstLine.endsWith(".")
                    && !firstLine.endsWith(",");

            String heading = firstLineIsTitle && blockLines.length > 1 ? firstLine : null;
            String body    = (heading != null) ? blockLines[1].strip() : trimmed;

            sections.add(new KnowledgeSection(file, heading, body, sectionIdx++));
        }

        log.debug("Parsed {} plain-text sections from {}", sections.size(), file.getFileName());
        return List.copyOf(sections);
    }
}
