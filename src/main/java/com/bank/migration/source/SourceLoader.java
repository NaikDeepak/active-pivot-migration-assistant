package com.bank.migration.source;

import com.bank.migration.compiler.CompilerError;

/**
 * Loads the minimum source context required to understand and fix a {@link CompilerError}.
 */
public interface SourceLoader {

    SourceSnippet load(CompilerError error);
}
