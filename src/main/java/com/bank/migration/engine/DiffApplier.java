package com.bank.migration.engine;

import java.nio.file.Path;

/**
 * Applies a unified diff patch to a source file on disk.
 */
public interface DiffApplier {

    ApplyResult apply(Path sourceFile, String diffPatch);
}
