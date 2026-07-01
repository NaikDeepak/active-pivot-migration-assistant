package com.bank.migration.similarity;

import com.bank.migration.compiler.CompilerError;

import java.util.List;

/**
 * Searches the JAR index for replacement candidates for a missing class or method.
 */
public interface SimilaritySearch {

    /**
     * Returns up to {@code topK} candidates, sorted descending by confidence/score.
     * Returns an empty list when no JAR indexes are loaded or nothing scores above
     * {@link Confidence#INSUFFICIENT}.
     */
    List<CandidateMatch> findCandidates(CompilerError error, int topK);

    default List<CandidateMatch> findCandidates(CompilerError error) {
        return findCandidates(error, 5);
    }
}
