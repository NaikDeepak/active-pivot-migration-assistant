package com.bank.migration.jar;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.jar.model.JarIndex;
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
 * {@link JarIndexStore} that persists each {@link JarIndex} as a JSON file under
 * {@code migration.jar-index-dir}. File name format: {@code <jarFileName>.json}.
 *
 * <p>Staleness detection: the stored {@code jarSizeBytes} and {@code jarLastModified}
 * are compared against the live file. If either differs the stored index is considered stale
 * and {@link #isAlreadyIndexed} returns {@code false}.
 */
@Component
public class FileSystemJarIndexStore implements JarIndexStore {

    private static final Logger log = LoggerFactory.getLogger(FileSystemJarIndexStore.class);

    private final MigrationProperties props;
    private final ObjectMapper objectMapper;

    public FileSystemJarIndexStore(MigrationProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(JarIndex index) {
        Path indexDir = ensureIndexDir();
        Path target = indexDir.resolve(index.jarFileName() + ".json");
        try {
            objectMapper.writeValue(target.toFile(), index);
            log.info("Saved JAR index: {} ({} classes)", target.getFileName(), index.indexedClassCount());
        } catch (IOException e) {
            throw new JarIndexException("Cannot write JAR index to: " + target, e);
        }
    }

    @Override
    public List<JarIndex> loadAll() {
        Path indexDir = props.jarIndexDir();
        if (!Files.isDirectory(indexDir)) {
            log.debug("JAR index directory does not exist yet: {}", indexDir);
            return List.of();
        }

        List<JarIndex> indexes = new ArrayList<>();
        try (Stream<Path> files = Files.list(indexDir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                 .sorted()
                 .forEach(file -> {
                     try {
                         indexes.add(objectMapper.readValue(file.toFile(), JarIndex.class));
                         log.debug("Loaded JAR index: {}", file.getFileName());
                     } catch (IOException e) {
                         log.warn("Skipping corrupt index file {}: {}", file.getFileName(), e.getMessage());
                     }
                 });
        } catch (IOException e) {
            throw new JarIndexException("Cannot list JAR index directory: " + indexDir, e);
        }

        log.info("Loaded {} JAR index(es) from {}", indexes.size(), indexDir);
        return List.copyOf(indexes);
    }

    @Override
    public boolean isAlreadyIndexed(Path jarFile) {
        Path indexFile = props.jarIndexDir().resolve(jarFile.getFileName().toString() + ".json");
        if (!Files.exists(indexFile)) return false;

        try {
            JarIndex stored = objectMapper.readValue(indexFile.toFile(), JarIndex.class);
            long currentSize  = Files.size(jarFile);
            long currentMtime = Files.getLastModifiedTime(jarFile).toMillis();
            boolean fresh = stored.jarSizeBytes() == currentSize
                         && stored.jarLastModified() == currentMtime;
            if (!fresh) {
                log.info("JAR index is stale for {} — will re-index", jarFile.getFileName());
            }
            return fresh;
        } catch (IOException e) {
            log.warn("Cannot read existing index for {}: {}", jarFile.getFileName(), e.getMessage());
            return false;
        }
    }

    private Path ensureIndexDir() {
        Path dir = props.jarIndexDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new JarIndexException("Cannot create JAR index directory: " + dir, e);
        }
        return dir;
    }
}
