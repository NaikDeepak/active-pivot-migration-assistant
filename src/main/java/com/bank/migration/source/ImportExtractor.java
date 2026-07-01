package com.bank.migration.source;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Extracts import statements from a Java source file's lines.
 * Only imports are returned — the package declaration and class body are excluded.
 */
@Component
class ImportExtractor {

    List<String> extract(List<String> fileLines) {
        return fileLines.stream()
                .filter(line -> line.stripLeading().startsWith("import ")
                        && line.strip().endsWith(";"))
                .toList();
    }
}
