package com.bank.migration.prompt;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.fix.FixRecord;
import com.bank.migration.knowledge.KnowledgeMatch;
import com.bank.migration.similarity.CandidateMatch;
import com.bank.migration.source.SourceSnippet;

import java.util.List;

/**
 * Everything the prompt builder needs to construct one AI request.
 * Assembled by the migration engine before calling {@link PromptBuilder#build}.
 */
public record PromptContext(
        CompilerError error,
        SourceSnippet sourceSnippet,
        List<KnowledgeMatch> knowledgeMatches,
        List<CandidateMatch> jarCandidates,
        List<FixRecord> previousFixes
) {}
