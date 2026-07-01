package com.bank.migration.fix;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link FixHistory} that persists each {@link FixRecord} as a JSON file under
 * {@code migration.fixes-dir}.
 *
 * <p>File name: {@code <sanitized-signature>_<epoch-ms>.json}
 *
 * <p>Similarity matching:
 * <ul>
 *   <li>100 pts — identical {@code errorSignature}</li>
 *   <li> 50 pts — same source file name (first token of the signature)</li>
 *   <li> 25 pts — same "cannot find" category (class / method / package)</li>
 * </ul>
 */
@Component("fileSystemFixHistory")
public class FileSystemFixHistory implements FixHistory {

    private static final Logger log = LoggerFactory.getLogger(FileSystemFixHistory.class);

    private final MigrationProperties props;
    private final ObjectMapper objectMapper;

    public FileSystemFixHistory(MigrationProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(FixRecord fix) {
        Path dir = ensureFixDir();
        String safeName = sanitize(fix.errorSignature());
        String fileName = safeName + "_" + System.nanoTime() + ".json";
        Path target = dir.resolve(fileName);
        try {
            objectMapper.writeValue(target.toFile(), fix);
            log.info("Recorded fix: {}", target.getFileName());
        } catch (IOException e) {
            log.warn("Failed to persist fix record to {}: {}", target, e.getMessage());
        }
    }

    @Override
    public List<FixRecord> findSimilar(CompilerError error, int topK) {
        Path dir = props.fixesDir();
        if (!Files.isDirectory(dir)) return List.of();

        List<ScoredFix> scored = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                 .forEach(file -> {
                     try {
                         FixRecord fix = objectMapper.readValue(file.toFile(), FixRecord.class);
                         int score = similarity(fix, error);
                         if (score > 0) scored.add(new ScoredFix(fix, score));
                     } catch (IOException e) {
                         log.debug("Skipping corrupt fix file {}: {}", file.getFileName(), e.getMessage());
                     }
                 });
        } catch (IOException e) {
            log.warn("Cannot list fixes directory: {}", e.getMessage());
            return List.of();
        }

        return scored.stream()
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .limit(topK)
                .map(ScoredFix::fix)
                .toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int similarity(FixRecord fix, CompilerError error) {
        int score = 0;

        // Exact signature match
        if (fix.errorSignature().equals(error.signature())) score += 100;

        // Same source file name (first segment of signature)
        String fixFile    = firstToken(fix.errorSignature());
        String errorFile  = firstToken(error.signature());
        if (!fixFile.isEmpty() && fixFile.equals(errorFile)) score += 50;

        // Same error category (same message up to first colon or space)
        String fixCategory   = category(fix.errorMessage());
        String errorCategory = category(error.message());
        if (!fixCategory.isEmpty() && fixCategory.equals(errorCategory)) score += 25;

        return score;
    }

    private String firstToken(String signature) {
        int colon = signature.indexOf(':');
        return colon > 0 ? signature.substring(0, colon) : signature;
    }

    private String category(String message) {
        if (message == null) return "";
        String lower = message.toLowerCase();
        if (lower.contains("cannot find symbol")) return "cannot-find-symbol";
        if (lower.contains("does not exist"))     return "does-not-exist";
        if (lower.contains("incompatible types")) return "incompatible-types";
        return lower.split("[:\\s]")[0];
    }

    private String sanitize(String input) {
        return input.replaceAll("[^A-Za-z0-9_.-]", "_")
                    .replaceAll("_{2,}", "_")
                    .substring(0, Math.min(input.length(), 80));
    }

    private Path ensureFixDir() {
        Path dir = props.fixesDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create fixes directory: " + dir, e);
        }
        return dir;
    }

    private record ScoredFix(FixRecord fix, int score) {}
}
