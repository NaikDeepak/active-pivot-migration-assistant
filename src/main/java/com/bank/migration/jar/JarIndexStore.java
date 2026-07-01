package com.bank.migration.jar;

import com.bank.migration.jar.model.JarIndex;

import java.nio.file.Path;
import java.util.List;

/**
 * Persists and retrieves {@link JarIndex} objects.
 */
public interface JarIndexStore {

    /** Serialises {@code index} to the store. Overwrites any existing entry for the same JAR. */
    void save(JarIndex index);

    /** Loads all available indexes from the store. */
    List<JarIndex> loadAll();

    /**
     * Returns {@code true} when a fresh (non-stale) index already exists for the given JAR.
     * Staleness is detected by comparing the JAR's current size and last-modified timestamp
     * against the values recorded in the stored index.
     */
    boolean isAlreadyIndexed(Path jarFile);
}
