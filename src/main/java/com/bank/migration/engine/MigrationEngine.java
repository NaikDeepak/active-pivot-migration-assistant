package com.bank.migration.engine;

import com.bank.migration.console.SessionState;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Orchestrates one full fix cycle: load source → gather context → call AI →
 * show diff → apply → recompile → record.
 */
public interface MigrationEngine {

    /**
     * Attempts to fix the error currently focused in {@code session}.
     * Uses {@code in}/{@code out} for the interactive approval prompt.
     *
     * @return the outcome; never throws for expected failures (AI errors, bad diffs, etc.)
     */
    FixOutcome fixNextError(SessionState session, BufferedReader in, PrintStream out)
            throws IOException;
}
