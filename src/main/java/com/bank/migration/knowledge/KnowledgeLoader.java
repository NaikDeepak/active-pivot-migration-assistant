package com.bank.migration.knowledge;

import com.bank.migration.compiler.CompilerError;

import java.util.List;

/**
 * Finds migration-knowledge sections relevant to a given compiler error.
 */
public interface KnowledgeLoader {

    /**
     * Returns the top {@code topK} knowledge sections most relevant to {@code error},
     * sorted descending by relevance score. Returns an empty list when the knowledge
     * directory is empty or no sections score above zero.
     */
    List<KnowledgeMatch> findRelevant(CompilerError error, int topK);

    default List<KnowledgeMatch> findRelevant(CompilerError error) {
        return findRelevant(error, 5);
    }
}
