package com.bank.migration.jar;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.jar.model.IndexedClass;
import com.bank.migration.jar.model.JarIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileSystemJarIndexStoreTest {

    @TempDir
    Path indexDir;

    private FileSystemJarIndexStore store;

    @BeforeEach
    void setUp() {
        MigrationProperties props = mock(MigrationProperties.class);
        when(props.jarIndexDir()).thenReturn(indexDir);

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        store = new FileSystemJarIndexStore(props, mapper);
    }

    private JarIndex minimalIndex(String jarName) {
        return new JarIndex(
                jarName, "/path/" + jarName,
                1024L, 1_700_000_000_000L,
                "2026-07-01T10:00:00Z",
                10, 2,
                List.of()
        );
    }

    @Test
    void savesIndexAsJsonFile() throws Exception {
        store.save(minimalIndex("pivot-5.10.jar"));

        assertThat(indexDir.resolve("pivot-5.10.jar.json")).exists();
    }

    @Test
    void roundTripPreservesClassName() throws Exception {
        JarIndex original = new JarIndex(
                "test.jar", "/p/test.jar", 512L, 1_000L, "2026-07-01T00:00:00Z",
                5, 0,
                List.of(new IndexedClass(
                        "com.example.Foo", "com.example", "Foo",
                        com.bank.migration.jar.model.ClassKind.CLASS,
                        null, List.of(), List.of(), List.of(), List.of()
                ))
        );
        store.save(original);

        List<JarIndex> loaded = store.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().classes()).hasSize(1);
        assertThat(loaded.getFirst().classes().getFirst().fullyQualifiedName())
                .isEqualTo("com.example.Foo");
    }

    @Test
    void loadAllReturnsEmptyWhenDirectoryEmpty() {
        assertThat(store.loadAll()).isEmpty();
    }

    @Test
    void loadAllSkipsCorruptFiles() throws Exception {
        Files.writeString(indexDir.resolve("bad.json"), "{ this is not valid json");
        store.save(minimalIndex("good.jar"));

        List<JarIndex> loaded = store.loadAll();

        assertThat(loaded).hasSize(1);
        assertThat(loaded.getFirst().jarFileName()).isEqualTo("good.jar");
    }

    @Test
    void isAlreadyIndexedReturnsFalseWhenNoIndexExists() throws Exception {
        Path fakeJar = indexDir.resolve("new.jar");
        Files.writeString(fakeJar, "fake");
        assertThat(store.isAlreadyIndexed(fakeJar)).isFalse();
    }

    @Test
    void isAlreadyIndexedReturnsTrueWhenSizeAndMtimeMatch() throws Exception {
        Path fakeJar = indexDir.resolve("stable.jar");
        Files.writeString(fakeJar, "fake jar content");

        JarIndex index = new JarIndex(
                "stable.jar", fakeJar.toString(),
                Files.size(fakeJar),
                Files.getLastModifiedTime(fakeJar).toMillis(),
                "2026-07-01T00:00:00Z", 1, 0, List.of()
        );
        store.save(index);

        assertThat(store.isAlreadyIndexed(fakeJar)).isTrue();
    }

    @Test
    void isAlreadyIndexedReturnsFalseWhenSizeChanges() throws Exception {
        Path fakeJar = indexDir.resolve("changed.jar");
        Files.writeString(fakeJar, "v1");

        JarIndex staleIndex = new JarIndex(
                "changed.jar", fakeJar.toString(),
                999L,  // wrong size
                Files.getLastModifiedTime(fakeJar).toMillis(),
                "2026-07-01T00:00:00Z", 1, 0, List.of()
        );
        store.save(staleIndex);

        assertThat(store.isAlreadyIndexed(fakeJar)).isFalse();
    }

    @Test
    void saveOverwritesExistingIndex() throws Exception {
        store.save(minimalIndex("pivot.jar"));
        store.save(minimalIndex("pivot.jar"));   // second save, same file name

        List<JarIndex> loaded = store.loadAll();
        assertThat(loaded).hasSize(1);  // still one file, not two
    }

    @Test
    void loadAllReturnsEmptyWhenDirectoryMissing() {
        MigrationProperties props = mock(MigrationProperties.class);
        when(props.jarIndexDir()).thenReturn(Path.of("/nonexistent/dir"));
        FileSystemJarIndexStore s = new FileSystemJarIndexStore(props,
                new ObjectMapper().registerModule(new JavaTimeModule()));

        assertThat(s.loadAll()).isEmpty();
    }
}
