package com.bank.migration.knowledge;

import java.util.Set;

/**
 * A {@link KnowledgeSection} that matched a compiler-error query, together with its relevance score.
 *
 * <p>{@code score} is a non-negative value: higher means more relevant.
 * {@code matchedTerms} shows which query terms contributed to the score (useful for debugging).
 */
public record KnowledgeMatch(
        KnowledgeSection section,
        double score,
        Set<String> matchedTerms
) implements Comparable<KnowledgeMatch> {

    @Override
    public int compareTo(KnowledgeMatch other) {
        // Descending by score; stable secondary sort by file + section index
        int cmp = Double.compare(other.score, this.score);
        if (cmp != 0) return cmp;
        int fileCmp = section.sourceFile().compareTo(other.section.sourceFile());
        if (fileCmp != 0) return fileCmp;
        return Integer.compare(section.sectionIndex(), other.section.sectionIndex());
    }

    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(section.label()).append(" ---\n");
        if (section.heading() != null) {
            sb.append(section.heading().strip()).append("\n");
        }
        sb.append(section.body().strip()).append("\n");
        return sb.toString();
    }
}
