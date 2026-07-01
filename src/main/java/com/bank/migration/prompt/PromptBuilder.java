package com.bank.migration.prompt;

/**
 * Assembles a {@link MigrationPrompt} from all available context for one compiler error.
 */
public interface PromptBuilder {

    MigrationPrompt build(PromptContext context);
}
