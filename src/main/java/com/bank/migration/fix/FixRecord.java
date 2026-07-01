package com.bank.migration.fix;

/**
 * An immutable record of a successfully applied fix.
 *
 * <p>Stored to JSON so the prompt builder can include similar past fixes,
 * and so the user can audit what the tool changed.
 */
public record FixRecord(
        String errorSignature,        // from CompilerError.signature() — deduplication key
        String sourceFilePath,
        String errorMessage,
        String errorDetails,          // joined detail lines
        String diffPatch,             // unified diff that was applied
        String appliedAt,             // ISO-8601 datetime
        boolean compilationSucceeded  // true when re-compile after the fix passed
) {}
