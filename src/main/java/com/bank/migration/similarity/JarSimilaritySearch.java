package com.bank.migration.similarity;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.jar.JarIndexStore;
import com.bank.migration.jar.model.JarIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link SimilaritySearch} that searches all loaded {@link JarIndex}es.
 *
 * <p>Workflow per call:
 * <ol>
 *   <li>Extract a {@link SearchQuery} from the compiler error.</li>
 *   <li>Load all JAR indexes from the store.</li>
 *   <li>Score every indexed class against the query.</li>
 *   <li>Return the top-K results with {@link Confidence} above INSUFFICIENT.</li>
 * </ol>
 *
 * <p>When no indexes are loaded or nothing scores above INSUFFICIENT, the result is empty
 * and the caller (Phase 7 prompt builder) must include an explicit "I need additional context"
 * note in the AI prompt.
 */
@Component
public class JarSimilaritySearch implements SimilaritySearch {

    private static final Logger log = LoggerFactory.getLogger(JarSimilaritySearch.class);

    private final JarIndexStore store;
    private final QueryExtractor queryExtractor;
    private final CandidateScorer scorer;

    public JarSimilaritySearch(
            JarIndexStore store,
            QueryExtractor queryExtractor,
            CandidateScorer scorer) {
        this.store = store;
        this.queryExtractor = queryExtractor;
        this.scorer = scorer;
    }

    @Override
    public List<CandidateMatch> findCandidates(CompilerError error, int topK) {
        SearchQuery query = queryExtractor.extract(error);

        if (query.isEmpty()) {
            log.debug("No searchable terms in error: {}", error.summary());
            return List.of();
        }

        List<JarIndex> indexes = store.loadAll();
        if (indexes.isEmpty()) {
            log.warn("No JAR indexes found — run 'scan <jar-path>' first");
            return List.of();
        }

        long totalClasses = indexes.stream().mapToLong(i -> i.classes().size()).sum();
        log.debug("Searching {} classes across {} JAR index(es) for query: class={}, method={}",
                totalClasses, indexes.size(), query.missingClassName(), query.missingMethodName());

        List<CandidateMatch> candidates = indexes.stream()
                .flatMap(idx -> idx.classes().stream())
                .map(cls -> scorer.score(cls, query))
                .filter(m -> m.confidence() != Confidence.INSUFFICIENT)
                .sorted()
                .limit(topK)
                .toList();

        log.info("Found {} candidate(s) for '{}'",
                candidates.size(),
                query.isClassSearch() ? query.missingClassName() : query.missingMethodName());

        return candidates;
    }
}
