package com.bank.migration.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores {@link KnowledgeSection}s against a set of query terms.
 *
 * <p>Scoring rules:
 * <ul>
 *   <li>Term found in heading: +3.0 per term</li>
 *   <li>Term found in body: +1.0 per term</li>
 *   <li>All matching is case-insensitive substring matching.</li>
 *   <li>Sections with score == 0 are excluded from results.</li>
 * </ul>
 */
@Component
class SectionScorer {

    private static final double HEADING_WEIGHT = 3.0;
    private static final double BODY_WEIGHT    = 1.0;

    List<KnowledgeMatch> score(List<KnowledgeSection> sections, Set<String> queryTerms, int topK) {
        if (queryTerms.isEmpty()) return List.of();

        List<KnowledgeMatch> matches = new ArrayList<>();
        for (KnowledgeSection section : sections) {
            KnowledgeMatch match = computeMatch(section, queryTerms);
            if (match.score() > 0) {
                matches.add(match);
            }
        }

        return matches.stream()
                .sorted()
                .limit(topK)
                .toList();
    }

    private KnowledgeMatch computeMatch(KnowledgeSection section, Set<String> queryTerms) {
        String headingLower = section.heading() != null ? section.heading().toLowerCase() : "";
        String bodyLower    = section.body().toLowerCase();

        Set<String> matched = new LinkedHashSet<>();
        double score = 0.0;

        for (String term : queryTerms) {
            String termLower = term.toLowerCase();
            boolean inHeading = !headingLower.isEmpty() && headingLower.contains(termLower);
            boolean inBody    = bodyLower.contains(termLower);

            if (inHeading) {
                matched.add(term);
                score += HEADING_WEIGHT;
            } else if (inBody) {
                matched.add(term);
                score += BODY_WEIGHT;
            }
        }

        return new KnowledgeMatch(section, score, Set.copyOf(matched));
    }
}
