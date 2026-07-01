package com.bank.migration.compiler;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw Maven output and extracts {@link CompilerError} instances.
 *
 * Maven's javac bridge emits error lines in this format:
 *   [ERROR] /abs/path/to/File.java:[42,15] cannot find symbol
 *
 * Followed by zero or more continuation lines (symbol:, location:, caret, etc.)
 * that do NOT start with a Maven log-level marker ([ERROR], [INFO], etc.).
 */
@Component
public class MavenOutputParser {

    // Captures: (1) file path  (2) line  (3) column  (4) message
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "\\[ERROR] (.+\\.java):\\[(\\d+),(\\d+)] (.+)"
    );

    public List<CompilerError> parse(String rawOutput) {
        List<CompilerError> errors = new ArrayList<>();
        String[] lines = rawOutput.split("\\r?\\n");

        int i = 0;
        while (i < lines.length) {
            Matcher m = ERROR_PATTERN.matcher(lines[i]);
            if (m.matches()) {
                Path sourceFile = Path.of(m.group(1));
                int line       = Integer.parseInt(m.group(2));
                int column     = Integer.parseInt(m.group(3));
                String message = m.group(4).trim();

                List<String> details = new ArrayList<>();
                i++;
                while (i < lines.length && isContinuationLine(lines[i])) {
                    String stripped = lines[i].strip();
                    if (!stripped.isEmpty()) {
                        details.add(stripped);
                    }
                    i++;
                }

                errors.add(new CompilerError(sourceFile, line, column, message, List.copyOf(details)));
            } else {
                i++;
            }
        }

        return List.copyOf(errors);
    }

    /** A continuation line carries detail for the preceding error.
     *  Maven log-level markers always start with '[', so anything else is detail. */
    private boolean isContinuationLine(String line) {
        return !line.startsWith("[");
    }
}
