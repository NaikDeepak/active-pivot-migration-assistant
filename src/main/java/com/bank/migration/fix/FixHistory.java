package com.bank.migration.fix;

import com.bank.migration.compiler.CompilerError;

import java.util.List;

/**
 * Stores and retrieves past successful fixes.
 *
 * <p>The prompt builder uses this to include proven fixes for similar errors,
 * which significantly increases AI accuracy for repeated error patterns.
 */
public interface FixHistory {

    /** Persists a fix record after successful compilation verification. */
    void record(FixRecord fix);

    /**
     * Returns up to {@code topK} fixes whose {@code errorSignature} or {@code errorMessage}
     * is similar to the given error, ordered most-recent first.
     */
    List<FixRecord> findSimilar(CompilerError error, int topK);

    default List<FixRecord> findSimilar(CompilerError error) {
        return findSimilar(error, 3);
    }
}
