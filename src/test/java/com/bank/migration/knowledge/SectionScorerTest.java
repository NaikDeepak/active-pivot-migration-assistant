package com.bank.migration.knowledge;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SectionScorerTest {

    private final SectionScorer scorer = new SectionScorer();
    private static final Path FILE = Path.of("/knowledge/guide.md");

    private KnowledgeSection section(String heading, String body, int idx) {
        return new KnowledgeSection(FILE, heading, body, idx);
    }

    @Test
    void headingMatchScoresHigherThanBodyMatch() {
        KnowledgeSection headingMatch = section("ActiveMonitor Migration", "Some details here.", 0);
        KnowledgeSection bodyMatch    = section("Unrelated Heading", "Use ActiveMonitor replacement.", 1);

        List<KnowledgeMatch> matches = scorer.score(
                List.of(bodyMatch, headingMatch), Set.of("ActiveMonitor"), 5);

        assertThat(matches.getFirst().section()).isEqualTo(headingMatch);
    }

    @Test
    void sectionsWithZeroScoreAreExcluded() {
        KnowledgeSection relevant  = section("ActiveMonitor", "Old API.", 0);
        KnowledgeSection irrelevant = section("DatastoreSetup", "Completely different.", 1);

        List<KnowledgeMatch> matches = scorer.score(
                List.of(relevant, irrelevant), Set.of("ActiveMonitor"), 5);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().section()).isEqualTo(relevant);
    }

    @Test
    void topKLimitsResultCount() {
        List<KnowledgeSection> sections = List.of(
                section("Alpha migration", "term", 0),
                section("Beta migration", "term", 1),
                section("Gamma migration", "term", 2),
                section("Delta migration", "term", 3)
        );

        List<KnowledgeMatch> matches = scorer.score(sections, Set.of("term"), 2);

        assertThat(matches).hasSize(2);
    }

    @Test
    void matchedTermsArePopulated() {
        KnowledgeSection s = section("ActiveMonitor Guide", "body text.", 0);

        List<KnowledgeMatch> matches = scorer.score(List.of(s), Set.of("ActiveMonitor", "Guide"), 5);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().matchedTerms()).contains("ActiveMonitor", "Guide");
    }

    @Test
    void caseInsensitiveMatching() {
        KnowledgeSection s = section("activemonitor", "body", 0);

        List<KnowledgeMatch> matches = scorer.score(List.of(s), Set.of("ActiveMonitor"), 5);

        assertThat(matches).hasSize(1);
    }

    @Test
    void emptyQueryTermsReturnsEmpty() {
        KnowledgeSection s = section("ActiveMonitor", "body", 0);

        List<KnowledgeMatch> matches = scorer.score(List.of(s), Set.of(), 5);

        assertThat(matches).isEmpty();
    }

    @Test
    void multipleTermsAccumulateScore() {
        // rich heading matches 3 non-overlapping terms → score 9.0
        // sparse has ActiveMonitor only in body → score 1.0
        KnowledgeSection rich   = section("ActiveMonitor Replacement Guide", "Use the new API.", 0);
        KnowledgeSection sparse = section("Quick Note", "ActiveMonitor changed.", 1);

        List<KnowledgeMatch> matches = scorer.score(
                List.of(rich, sparse), Set.of("ActiveMonitor", "Replacement", "Guide"), 5);

        assertThat(matches.getFirst().score()).isGreaterThan(matches.get(1).score());
    }

    @Test
    void resultIsSortedDescendingByScore() {
        List<KnowledgeSection> sections = List.of(
                section("Only one term", "just monitor here", 0),
                section("ActiveMonitor Migration Guide", "full body with Monitor and Active", 1)
        );

        List<KnowledgeMatch> matches = scorer.score(
                sections, Set.of("ActiveMonitor", "Monitor", "Active"), 5);

        for (int i = 0; i < matches.size() - 1; i++) {
            assertThat(matches.get(i).score()).isGreaterThanOrEqualTo(matches.get(i + 1).score());
        }
    }
}
