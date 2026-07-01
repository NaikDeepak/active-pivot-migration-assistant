package com.bank.migration.source;

/**
 * A single line of source code paired with its 1-based line number in the original file.
 */
public record NumberedLine(int number, String content) {

    /** Returns a fixed-width display string suitable for a console diff or AI prompt. */
    public String toDisplayString() {
        return String.format("%4d: %s", number, content);
    }
}
