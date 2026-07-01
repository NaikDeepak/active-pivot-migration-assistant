package com.bank.migration.similarity;

import com.bank.migration.jar.model.IndexedClass;
import com.bank.migration.jar.model.IndexedMethod;

/**
 * A JAR class (and optionally a specific method on it) that could replace a missing API.
 *
 * <p>{@code candidateMethod} is non-null only when the query was for a specific method name.
 * {@code reason} is a human-readable explanation included in the AI prompt so the model
 * knows why this candidate was selected rather than guessing on its own.
 */
public record CandidateMatch(
        IndexedClass candidateClass,
        IndexedMethod candidateMethod,   // null for class-level candidates
        Confidence confidence,
        double score,
        String reason
) implements Comparable<CandidateMatch> {

    @Override
    public int compareTo(CandidateMatch other) {
        // Descending by score; stable secondary sort by FQN
        int cmp = Double.compare(other.score, this.score);
        return cmp != 0 ? cmp
                : candidateClass.fullyQualifiedName().compareTo(other.candidateClass.fullyQualifiedName());
    }

    /** Compact text for inclusion in an AI prompt. */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(confidence).append("] ");
        sb.append(candidateClass.fullyQualifiedName());
        if (candidateMethod != null) {
            sb.append("#").append(candidateMethod.signature());
        }
        sb.append("\n  Kind   : ").append(candidateClass.kind());
        if (!candidateClass.interfaceNames().isEmpty()) {
            sb.append("\n  Implements: ").append(String.join(", ", candidateClass.interfaceNames()));
        }
        sb.append("\n  Reason : ").append(reason);
        return sb.toString();
    }
}
