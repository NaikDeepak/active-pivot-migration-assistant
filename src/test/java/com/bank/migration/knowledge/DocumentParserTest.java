package com.bank.migration.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    // ── Markdown ──────────────────────────────────────────────────────────────

    @Test
    void markdownSplitsOnH2Headings() throws Exception {
        Path file = write("migration.md", """
                ## ActiveMonitor Migration
                Use IActivePivotMonitor instead.

                ## DatastoreBuilder Migration
                Replace with IDatastoreBuilder.
                """);

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isEqualTo("ActiveMonitor Migration");
        assertThat(sections.get(0).body()).contains("IActivePivotMonitor");
        assertThat(sections.get(1).heading()).isEqualTo("DatastoreBuilder Migration");
    }

    @Test
    void markdownPreambuleCapturedWithNullHeading() throws Exception {
        Path file = write("guide.md", """
                This is the introduction paragraph.

                ## Section One
                Body of section one.
                """);

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).heading()).isNull();
        assertThat(sections.get(0).body()).contains("introduction");
        assertThat(sections.get(1).heading()).isEqualTo("Section One");
    }

    @Test
    void markdownH1IsAlsoASectionHeading() throws Exception {
        Path file = write("top.md", """
                # Top Level
                Top content.
                """);

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().heading()).isEqualTo("Top Level");
    }

    @Test
    void markdownSectionIndexIsOrdinal() throws Exception {
        Path file = write("idx.md", """
                ## First
                body1
                ## Second
                body2
                ## Third
                body3
                """);

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).extracting(KnowledgeSection::sectionIndex)
                .containsExactly(0, 1, 2);
    }

    @Test
    void markdownEmptyBodyIsIncludedWhenHeadingExists() throws Exception {
        Path file = write("empty.md", """
                ## Heading With No Body
                ## Next Heading
                content
                """);

        List<KnowledgeSection> sections = parser.parse(file);
        // Both sections present even if first body is empty
        assertThat(sections).hasSize(2);
    }

    // ── Plain text ────────────────────────────────────────────────────────────

    @Test
    void plainTextSplitsOnBlankLines() throws Exception {
        Path file = write("notes.txt", """
                ActiveMonitor Replacement
                Use IActivePivotMonitor from the new API.

                Package Changes
                com.qfs has moved to com.activeviam.
                """);

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).hasSize(2);
    }

    @Test
    void plainTextShortFirstLineBecomesHeading() throws Exception {
        Path file = write("short.txt", """
                ActiveMonitor
                Replace with IActivePivotMonitor in all service classes.
                """);

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().heading()).isEqualTo("ActiveMonitor");
        assertThat(sections.getFirst().body()).contains("IActivePivotMonitor");
    }

    @Test
    void plainTextLongFirstLineIsNotAHeading() throws Exception {
        // A line > 80 chars should stay as body, not become a heading
        String longLine = "This is a very long description that is definitely more than eighty characters long here.";
        Path file = write("long.txt", longLine + "\nSome more text.");

        List<KnowledgeSection> sections = parser.parse(file);

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().heading()).isNull();
        assertThat(sections.getFirst().body()).contains("very long description");
    }

    @Test
    void sourceFileIsRecordedOnEverySection() throws Exception {
        Path file = write("check.md", "## Only\nbody");
        List<KnowledgeSection> sections = parser.parse(file);
        assertThat(sections).allMatch(s -> file.equals(s.sourceFile()));
    }
}
