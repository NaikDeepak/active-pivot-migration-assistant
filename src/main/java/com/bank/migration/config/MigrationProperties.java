package com.bank.migration.config;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All tuneable parameters, bound from application.yml under prefix "migration".
 * Surfaces as a Spring bean; inject via constructor wherever needed.
 */
@ConfigurationProperties(prefix = "migration")
public record MigrationProperties(

        /** Absolute path to the project under migration. */
        Path projectRoot,

        /** Maven executable. Defaults to "mvn" (assumes it is on PATH). */
        @DefaultValue("mvn") String mavenExecutable,

        /** Extra Maven goals prepended before "compile" (e.g. "clean"). */
        @DefaultValue("clean") List<String> mavenPreGoals,

        /** Where Markdown/TXT migration knowledge docs live. */
        @DefaultValue("${user.home}/.migration-assistant/knowledge") Path knowledgeDir,

        /** Where JAR index JSON files are written and read. */
        @DefaultValue("${user.home}/.migration-assistant/jar-index") Path jarIndexDir,

        /** Where successful-fix JSON files are stored. */
        @DefaultValue("${user.home}/.migration-assistant/fixes") Path fixesDir,

        /** Directory containing ActivePivot JAR files to index. */
        @DefaultValue("${user.home}/.migration-assistant/jars") Path jarsDir,

        /** Lines of surrounding context to include when loading a source file. */
        @DefaultValue("100") int sourceSurroundingLines,

        /** AI provider configuration. */
        AiProperties ai,

        /** Console / display settings. */
        ConsoleProperties console

) {

    public record AiProperties(
            /** Provider: MOCK | CLAUDE */
            @DefaultValue("MOCK") String provider,
            /** Anthropic API key — required when provider=CLAUDE. */
            String apiKey,
            /** Model id, e.g. claude-opus-4-8 */
            @DefaultValue("claude-sonnet-4-6") String model,
            /** Hard cap on tokens sent per prompt. */
            @DefaultValue("8000") int maxInputTokens,
            /** Hard cap on tokens generated per response. */
            @DefaultValue("2000") int maxOutputTokens
    ) {}

    public record ConsoleProperties(
            /** Whether to show diffs in colour (requires ANSI terminal). */
            @DefaultValue("true") boolean colorDiff,
            /** Auto-apply fix without prompting when confidence score >= threshold. */
            @DefaultValue("0.95") double autoApplyThreshold
    ) {}
}
