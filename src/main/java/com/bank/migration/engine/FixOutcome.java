package com.bank.migration.engine;

import com.bank.migration.fix.FixRecord;

/**
 * The result of one {@link MigrationEngine#fixNextError} call.
 *
 * <p>Pattern-match on the subtypes to drive console output:
 * <pre>{@code
 * switch (outcome) {
 *     case FixOutcome.Success    s  -> ...
 *     case FixOutcome.NeedsContext n -> ...
 *     case FixOutcome.Rejected   r  -> ...
 *     case FixOutcome.ApplyFailed a -> ...
 *     case FixOutcome.NoError    ne -> ...
 * }
 * }</pre>
 */
public sealed interface FixOutcome
        permits FixOutcome.Success, FixOutcome.NeedsContext,
                FixOutcome.Rejected, FixOutcome.ApplyFailed, FixOutcome.NoError {

    /** Diff was applied and the fix was recorded (recompile may or may not have passed). */
    record Success(FixRecord fix) implements FixOutcome {}

    /** The AI could not produce a fix without additional JAR/knowledge context. */
    record NeedsContext(String request) implements FixOutcome {}

    /** The user declined to apply the proposed diff. */
    record Rejected() implements FixOutcome {}

    /** The diff could not be applied or the AI/source pipeline failed. */
    record ApplyFailed(String reason) implements FixOutcome {}

    /** The session has no current compiler error (need to run compile first). */
    record NoError() implements FixOutcome {}
}
