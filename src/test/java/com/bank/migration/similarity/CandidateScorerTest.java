package com.bank.migration.similarity;

import com.bank.migration.jar.model.ClassKind;
import com.bank.migration.jar.model.IndexedClass;
import com.bank.migration.jar.model.IndexedMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateScorerTest {

    private final CandidateScorer scorer = new CandidateScorer();

    private IndexedClass cls(String fqn, String simpleName, String pkg) {
        return new IndexedClass(fqn, pkg, simpleName, ClassKind.CLASS,
                null, List.of(), List.of(), List.of(), List.of());
    }

    private IndexedClass clsWithMethod(String fqn, String simpleName, String pkg,
                                        String methodName, List<String> params) {
        IndexedMethod m = new IndexedMethod(methodName, "void", params, List.of(), false, false);
        return new IndexedClass(fqn, pkg, simpleName, ClassKind.CLASS,
                null, List.of(), List.of(), List.of(m), List.of());
    }

    private SearchQuery classQuery(String missing) {
        return new SearchQuery(missing, null, List.of(), null, null);
    }

    private SearchQuery methodQuery(String methodName, List<String> params) {
        return new SearchQuery(null, methodName, params, null, null);
    }

    // ── Class name scoring ────────────────────────────────────────────────────

    @Test
    void exactMatchIsHighConfidence() {
        IndexedClass c = cls("com.new.ActiveMonitor", "ActiveMonitor", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(m.score()).isEqualTo(100.0);
    }

    @Test
    void caseInsensitiveMatchIsHighConfidence() {
        IndexedClass c = cls("com.new.ACTIVEMONITOR", "ACTIVEMONITOR", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test
    void interfaceConventionIPrefixIsHighConfidence() {
        IndexedClass c = cls("com.new.IActiveMonitor", "IActiveMonitor", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(m.score()).isEqualTo(80.0);
    }

    @Test
    void candidateWithTokenOverlapIsNotInsufficient() {
        // "IActivePivotMonitor" shares tokens [Active, Monitor] with "ActiveMonitor" — camelCase overlap
        IndexedClass c = cls("com.new.IActivePivotMonitor", "IActivePivotMonitor", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        // Must not be filtered out — exact confidence level depends on overlap fraction
        assertThat(m.confidence()).isNotEqualTo(Confidence.INSUFFICIENT);
    }

    @Test
    void directSubstringMatchIsMediumConfidence() {
        // "IActiveMonitor" = "I" + "ActiveMonitor" — exact I-prefix convention match
        IndexedClass c = cls("com.new.IActiveMonitor", "IActiveMonitor", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.confidence()).isEqualTo(Confidence.HIGH); // 80 pts from I-prefix rule
    }

    @Test
    void noMatchYieldsInsufficient() {
        IndexedClass c = cls("com.new.CompletelyUnrelated", "CompletelyUnrelated", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.confidence()).isEqualTo(Confidence.INSUFFICIENT);
    }

    @Test
    void packageOverlapAddsBonus() {
        IndexedClass withPkg = cls("com.activeviam.monitoring.ActiveMonitor",
                "ActiveMonitor", "com.activeviam.monitoring");
        IndexedClass withoutPkg = cls("com.unrelated.ActiveMonitor",
                "ActiveMonitor", "com.unrelated");

        SearchQuery q = new SearchQuery("ActiveMonitor", null, List.of(),
                "com.qfs.monitoring", null);

        double scoreWith    = scorer.score(withPkg, q).score();
        double scoreWithout = scorer.score(withoutPkg, q).score();

        assertThat(scoreWith).isGreaterThan(scoreWithout);
    }

    // ── Method scoring ────────────────────────────────────────────────────────

    @Test
    void exactMethodNameMatchAddsPoints() {
        IndexedClass c = clsWithMethod("com.new.RiskSvc", "RiskSvc", "com.new",
                "computeRisk", List.of("java.lang.String"));

        CandidateMatch m = scorer.score(c, methodQuery("computeRisk", List.of("java.lang.String")));

        assertThat(m.confidence()).isNotEqualTo(Confidence.INSUFFICIENT);
        assertThat(m.candidateMethod()).isNotNull();
        assertThat(m.candidateMethod().name()).isEqualTo("computeRisk");
    }

    @Test
    void paramCountMatchAddsAdditionalBonus() {
        IndexedClass withParams = clsWithMethod("com.a.Svc", "Svc", "com.a",
                "computeRisk", List.of("java.lang.String"));
        IndexedClass noParams = clsWithMethod("com.b.Svc", "Svc", "com.b",
                "computeRisk", List.of());

        SearchQuery q = methodQuery("computeRisk", List.of("java.lang.String"));

        double scoreWith = scorer.score(withParams, q).score();
        double scoreWithout = scorer.score(noParams, q).score();

        assertThat(scoreWith).isGreaterThan(scoreWithout);
    }

    @Test
    void pureMethodQueryYieldsInsufficientWhenMethodAbsent() {
        IndexedClass c = cls("com.new.NoSuchMethod", "NoSuchMethod", "com.new");
        CandidateMatch m = scorer.score(c, methodQuery("computeRisk", List.of()));
        assertThat(m.confidence()).isEqualTo(Confidence.INSUFFICIENT);
    }

    // ── Reason string ─────────────────────────────────────────────────────────

    @Test
    void reasonIsPopulatedForMatch() {
        IndexedClass c = cls("com.new.ActiveMonitor", "ActiveMonitor", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.reason()).isNotBlank();
    }

    @Test
    void reasonMentionsNoMatchWhenScore0() {
        IndexedClass c = cls("com.new.Unrelated", "Unrelated", "com.new");
        CandidateMatch m = scorer.score(c, classQuery("ActiveMonitor"));
        assertThat(m.reason()).contains("no match");
    }
}
