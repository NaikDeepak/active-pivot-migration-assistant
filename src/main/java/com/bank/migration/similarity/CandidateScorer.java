package com.bank.migration.similarity;

import com.bank.migration.jar.model.IndexedClass;
import com.bank.migration.jar.model.IndexedMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Scores an {@link IndexedClass} against a {@link SearchQuery} and returns a {@link CandidateMatch}.
 *
 * <p>Scoring breakdown (additive):
 * <ul>
 *   <li>Class name — up to 100 points</li>
 *   <li>Package similarity bonus — up to +20 points</li>
 *   <li>Method name match — up to +80 points (only when query has a method)</li>
 * </ul>
 *
 * <p>Matches below 20 points are returned as {@link Confidence#INSUFFICIENT} and filtered out
 * by {@link JarSimilaritySearch}.
 */
@Component
class CandidateScorer {

    // Splits PascalCase/camelCase: "ActiveMonitor" → ["Active", "Monitor"]
    private static final Pattern CAMEL = Pattern.compile(
            "(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"
    );

    CandidateMatch score(IndexedClass cls, SearchQuery query) {
        List<String> reasons = new ArrayList<>();
        double total = 0.0;
        IndexedMethod bestMethod = null;

        // ── Class name scoring ────────────────────────────────────────────
        if (query.isClassSearch()) {
            double cs = scoreClassName(cls.simpleName(), query.missingClassName());
            if (cs > 0) {
                total += cs;
                reasons.add(classReason(cls, query.missingClassName(), cs));
            }
        }

        // ── Package bonus ─────────────────────────────────────────────────
        double pkgBonus = packageBonus(cls, query.missingPackage());
        if (pkgBonus > 0) {
            total += pkgBonus;
            reasons.add("package overlap +" + (int) pkgBonus);
        }

        // ── Method scoring ────────────────────────────────────────────────
        if (query.isMethodSearch()) {
            Optional<IndexedMethod> methodMatch =
                    bestMethodMatch(cls, query.missingMethodName(), query.missingMethodParams());
            if (methodMatch.isPresent()) {
                IndexedMethod m = methodMatch.get();
                double ms = scoreMethod(m, query.missingMethodName(), query.missingMethodParams());
                total += ms;
                bestMethod = m;
                reasons.add("method '" + m.signature() + "' +" + (int) ms);
            }
        }

        // For pure method queries (no class name), a class with no method match scores 0
        if (query.isMethodSearch() && !query.isClassSearch() && bestMethod == null) {
            total = 0.0;
        }

        String reason = reasons.isEmpty() ? "no match" : String.join("; ", reasons);
        return new CandidateMatch(cls, bestMethod, Confidence.from(total), total, reason);
    }

    // ── Class name ────────────────────────────────────────────────────────────

    private double scoreClassName(String candidateSimple, String missing) {
        if (candidateSimple.equals(missing))              return 100.0;
        if (candidateSimple.equalsIgnoreCase(missing))    return 85.0;
        if (candidateSimple.equals("I" + missing))        return 80.0;  // interface convention
        if (candidateSimple.contains(missing))            return 65.0;  // e.g., IActivePivotMonitor ⊃ ActiveMonitor
        if (missing.contains(candidateSimple))            return 50.0;  // e.g., candidateSimple ⊂ missing
        double overlap = tokenOverlap(candidateSimple, missing);
        if (overlap >= 0.5) return 40.0 * overlap;
        return 0.0;
    }

    private String classReason(IndexedClass cls, String missing, double score) {
        return String.format("'%s' → %s (score %.0f)", missing, cls.simpleName(), score);
    }

    // ── Package bonus ─────────────────────────────────────────────────────────

    private double packageBonus(IndexedClass cls, String missingPackage) {
        if (missingPackage == null || cls.packageName() == null) return 0.0;
        List<String> missingSegs  = List.of(missingPackage.split("\\."));
        List<String> candidateSegs = List.of(cls.packageName().split("\\."));
        long overlap = missingSegs.stream().filter(candidateSegs::contains).count();
        return Math.min(20.0, overlap * 5.0);
    }

    // ── Method ────────────────────────────────────────────────────────────────

    private Optional<IndexedMethod> bestMethodMatch(
            IndexedClass cls, String methodName, List<String> params) {
        return cls.methods().stream()
                .filter(m -> scoreMethod(m, methodName, params) > 0)
                .max(java.util.Comparator.comparingDouble(m -> scoreMethod(m, methodName, params)));
    }

    private double scoreMethod(IndexedMethod method, String methodName, List<String> params) {
        boolean nameMatch = method.name().equals(methodName);
        boolean nameSuffix = !nameMatch && method.name().endsWith(
                Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1));

        if (!nameMatch && !nameSuffix) return 0.0;

        double base = nameMatch ? 60.0 : 30.0;
        if (!params.isEmpty() && method.parameterTypes().size() == params.size()) {
            base += 20.0;  // parameter count bonus
        }
        return base;
    }

    // ── CamelCase token overlap ───────────────────────────────────────────────

    private double tokenOverlap(String a, String b) {
        List<String> tokensA = camelTokens(a);
        List<String> tokensB = camelTokens(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0;
        long shared = tokensA.stream()
                .filter(t -> tokensB.stream().anyMatch(t::equalsIgnoreCase))
                .count();
        return (double) shared / Math.max(tokensA.size(), tokensB.size());
    }

    private List<String> camelTokens(String name) {
        return Arrays.stream(CAMEL.split(name))
                .filter(t -> t.length() >= 2)
                .toList();
    }
}
