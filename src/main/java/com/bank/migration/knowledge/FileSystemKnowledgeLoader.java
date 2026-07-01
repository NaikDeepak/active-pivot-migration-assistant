package com.bank.migration.knowledge;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@link KnowledgeLoader} that indexes {@code .md}, {@code .markdown}, and {@code .txt} files
 * from the configured {@code migration.knowledge-dir}.
 *
 * <p>Sections are indexed lazily on the first query and cached for the lifetime of the process.
 * Call {@link #reload()} to invalidate the cache when docs are updated at runtime.
 */
@Component
public class FileSystemKnowledgeLoader implements KnowledgeLoader {

    private static final Logger log = LoggerFactory.getLogger(FileSystemKnowledgeLoader.class);

    private final MigrationProperties props;
    private final DocumentParser documentParser;
    private final SectionScorer scorer;
    private final TermExtractor termExtractor;

    private List<KnowledgeSection> cachedSections;

    public FileSystemKnowledgeLoader(
            MigrationProperties props,
            DocumentParser documentParser,
            SectionScorer scorer,
            TermExtractor termExtractor) {
        this.props = props;
        this.documentParser = documentParser;
        this.scorer = scorer;
        this.termExtractor = termExtractor;
    }

    @Override
    public List<KnowledgeMatch> findRelevant(CompilerError error, int topK) {
        List<KnowledgeSection> sections = getSections();
        if (sections.isEmpty()) {
            log.warn("Knowledge index is empty — add .md or .txt files to: {}", props.knowledgeDir());
            return List.of();
        }

        Set<String> terms = termExtractor.extract(error);
        log.debug("Querying {} sections with {} terms: {}", sections.size(), terms.size(), terms);

        List<KnowledgeMatch> matches = scorer.score(sections, terms, topK);
        log.debug("Found {} relevant sections", matches.size());
        return matches;
    }

    /** Forces a re-scan of the knowledge directory on the next query. */
    public synchronized void reload() {
        cachedSections = null;
        log.info("Knowledge cache invalidated — will reload on next query");
    }

    private synchronized List<KnowledgeSection> getSections() {
        if (cachedSections == null) {
            cachedSections = loadAllSections();
        }
        return cachedSections;
    }

    private List<KnowledgeSection> loadAllSections() {
        Path dir = props.knowledgeDir();
        if (!Files.isDirectory(dir)) {
            log.warn("Knowledge directory not found: {} — no migration docs will be used", dir);
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(dir)) {
            List<KnowledgeSection> all = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                .filter(this::isSupportedFormat)
                .sorted()
                .forEach(file -> {
                    List<KnowledgeSection> parsed = documentParser.parse(file);
                    all.addAll(parsed);
                    log.info("Indexed {} section(s) from {}", parsed.size(), file.getFileName());
                });

            log.info("Knowledge index ready: {} section(s) total from {}", all.size(), dir);
            return List.copyOf(all);
        } catch (IOException e) {
            throw new KnowledgeLoadException("Failed to walk knowledge directory: " + dir, e);
        }
    }

    private boolean isSupportedFormat(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt");
    }
}
