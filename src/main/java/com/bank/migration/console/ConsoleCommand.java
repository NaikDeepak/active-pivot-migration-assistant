package com.bank.migration.console;

import java.util.Arrays;
import java.util.Optional;

public enum ConsoleCommand {

    COMPILE ("compile",  "Run mvn clean compile and show errors"),
    SCAN    ("scan",     "Index all JARs in the configured jars directory"),
    FIX     ("fix",      "Attempt to fix the current compiler error using AI"),
    NEXT    ("next",     "Advance to the next compiler error"),
    HISTORY ("history",  "Show recent fix history for the current error"),
    STATUS  ("status",   "Show current session state"),
    HELP    ("help",     "Show available commands"),
    QUIT    ("quit",     "Exit the assistant");

    public final String keyword;
    public final String description;

    ConsoleCommand(String keyword, String description) {
        this.keyword = keyword;
        this.description = description;
    }

    public static Optional<ConsoleCommand> parse(String input) {
        if (input == null) return Optional.empty();
        String lower = input.strip().toLowerCase();
        if (lower.equals("exit")) return Optional.of(QUIT);
        return Arrays.stream(values())
                .filter(c -> c.keyword.equals(lower))
                .findFirst();
    }
}
