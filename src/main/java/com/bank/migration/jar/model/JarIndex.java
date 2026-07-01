package com.bank.migration.jar.model;

import java.util.List;

/**
 * Complete index of one JAR file.
 *
 * <p>{@code jarSizeBytes} and {@code jarLastModified} are used by {@code JarIndexStore}
 * to detect whether a JAR has changed since it was last indexed.
 */
public record JarIndex(
        String jarFileName,
        String jarAbsolutePath,
        long jarSizeBytes,
        long jarLastModified,
        String indexedAt,
        int totalClassesScanned,
        int skippedClasses,
        List<IndexedClass> classes
) {

    public int indexedClassCount() {
        return classes.size();
    }
}
