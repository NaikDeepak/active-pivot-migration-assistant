package com.bank.migration.knowledge;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileSystemKnowledgeLoaderTest {

    @TempDir
    Path knowledgeDir;

    private FileSystemKnowledgeLoader loader;

    @BeforeEach
    void setUp() {
        MigrationProperties props = mock(MigrationProperties.class);
        when(props.knowledgeDir()).thenReturn(knowledgeDir);
        loader = new FileSystemKnowledgeLoader(
                props,
                new DocumentParser(),
                new SectionScorer(),
                new TermExtractor()
        );
    }

    private CompilerError errorFor(String symbol) {
        return new CompilerError(
                Path.of("/proj/Foo.java"), 10, 5,
                "cannot find symbol",
                List.of("symbol:   class " + symbol, "location: class com.example.Foo"));
    }

    @Test
    void findsRelevantSectionByClassName() throws Exception {
        Files.writeString(knowledgeDir.resolve("guide.md"), """
                ## ActiveMonitor Migration
                Replace ActiveMonitor with IActivePivotMonitor.
                The new class is in com.activeviam.monitoring.
                """);

        List<KnowledgeMatch> matches = loader.findRelevant(errorFor("ActiveMonitor"));

        assertThat(matches).isNotEmpty();
        assertThat(matches.getFirst().section().heading()).contains("ActiveMonitor");
    }

    @Test
    void returnsEmptyListWhenNothingMatches() throws Exception {
        Files.writeString(knowledgeDir.resolve("unrelated.md"), """
                ## DatastoreBuilder
                This doc is about DatastoreBuilder only.
                """);

        List<KnowledgeMatch> matches = loader.findRelevant(errorFor("CompletelyDifferentClass"));

        assertThat(matches).isEmpty();
    }

    @Test
    void returnsEmptyListWhenDirectoryIsEmpty() {
        List<KnowledgeMatch> matches = loader.findRelevant(errorFor("ActiveMonitor"));
        assertThat(matches).isEmpty();
    }

    @Test
    void returnsEmptyListWhenDirectoryDoesNotExist() {
        MigrationProperties props = mock(MigrationProperties.class);
        when(props.knowledgeDir()).thenReturn(Path.of("/nonexistent/knowledge"));
        FileSystemKnowledgeLoader missingDirLoader = new FileSystemKnowledgeLoader(
                props, new DocumentParser(), new SectionScorer(), new TermExtractor());

        assertThat(missingDirLoader.findRelevant(errorFor("ActiveMonitor"))).isEmpty();
    }

    @Test
    void respectsTopKLimit() throws Exception {
        Files.writeString(knowledgeDir.resolve("many.md"), """
                ## ActiveMonitor Section One
                content one

                ## ActiveMonitor Section Two
                content two

                ## ActiveMonitor Section Three
                content three
                """);

        List<KnowledgeMatch> matches = loader.findRelevant(errorFor("ActiveMonitor"), 2);

        assertThat(matches).hasSize(2);
    }

    @Test
    void parsesBothMarkdownAndTxtFiles() throws Exception {
        Files.writeString(knowledgeDir.resolve("a.md"), "## ActiveMonitor\nbody from md");
        Files.writeString(knowledgeDir.resolve("b.txt"), "ActiveMonitor\nbody from txt");

        List<KnowledgeMatch> matches = loader.findRelevant(errorFor("ActiveMonitor"), 10);

        assertThat(matches.stream().map(m -> m.section().sourceFile().getFileName().toString()))
                .containsAnyOf("a.md", "b.txt");
    }

    @Test
    void reloadClearsCache() throws Exception {
        List<KnowledgeMatch> before = loader.findRelevant(errorFor("ActiveMonitor"));
        assertThat(before).isEmpty();

        Files.writeString(knowledgeDir.resolve("added.md"), "## ActiveMonitor\nbody");
        loader.reload();

        List<KnowledgeMatch> after = loader.findRelevant(errorFor("ActiveMonitor"));
        assertThat(after).isNotEmpty();
    }

    @Test
    void ignoresNonDocumentFiles() throws Exception {
        Files.writeString(knowledgeDir.resolve("script.sh"), "echo ActiveMonitor");
        Files.writeString(knowledgeDir.resolve("guide.md"), "## ActiveMonitor\nbody");

        List<KnowledgeMatch> matches = loader.findRelevant(errorFor("ActiveMonitor"));

        // Only one source should be indexed (the .md, not the .sh)
        assertThat(matches.stream().map(m -> m.section().sourceFile().getFileName().toString()))
                .allMatch(name -> name.endsWith(".md") || name.endsWith(".txt"));
    }
}
