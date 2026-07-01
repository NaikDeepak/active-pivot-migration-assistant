package com.bank.migration.jar;

import com.bank.migration.jar.model.JarIndex;

import java.nio.file.Path;

/**
 * Reflects all public API from a JAR file and returns a structured {@link JarIndex}.
 */
public interface JarIndexer {

    JarIndex index(Path jarFile);
}
