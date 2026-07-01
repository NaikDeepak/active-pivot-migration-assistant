package com.bank.migration.compiler;

import java.nio.file.Path;
import java.util.List;

/**
 * A single javac error extracted from Maven's output.
 * {@code details} captures continuation lines (symbol:, location:, caret, etc.).
 */
public record CompilerError(
        Path sourceFile,
        int line,
        int column,
        String message,
        List<String> details
) {

    /** Stable key used for deduplication and fix-history lookup. */
    public String signature() {
        return sourceFile.getFileName() + ":" + line + ":" + column + ":" + message;
    }

    /** One-line human-readable summary. */
    public String summary() {
        return sourceFile.getFileName() + ":" + line + "  " + message;
    }
}
