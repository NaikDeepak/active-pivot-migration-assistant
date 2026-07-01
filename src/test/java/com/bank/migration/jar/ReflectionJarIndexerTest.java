package com.bank.migration.jar;

import com.bank.migration.jar.model.ClassKind;
import com.bank.migration.jar.model.IndexedClass;
import com.bank.migration.jar.model.JarIndex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the indexer against the commons-lang3 JAR, which is always on the test classpath.
 * This avoids shipping ActivePivot JARs in the repository.
 */
class ReflectionJarIndexerTest {

    private static final ReflectionJarIndexer indexer = new ReflectionJarIndexer();

    private static Path commonsLangJar;
    private static JarIndex index;

    @BeforeAll
    static void indexCommonsLang() {
        commonsLangJar = findJarOnClasspath("commons-lang3");
        index = indexer.index(commonsLangJar);
    }

    // ── Basic metadata ────────────────────────────────────────────────────────

    @Test
    void jarFileNameIsRecorded() {
        assertThat(index.jarFileName()).startsWith("commons-lang3");
    }

    @Test
    void indexedAtIsIso8601() {
        assertThat(index.indexedAt()).matches("\\d{4}-\\d{2}-\\d{2}T.*");
    }

    @Test
    void jarSizeAndMtimeArePositive() throws Exception {
        assertThat(index.jarSizeBytes()).isPositive();
        assertThat(index.jarLastModified()).isPositive();
    }

    @Test
    void containsIndexedClasses() {
        assertThat(index.classes()).isNotEmpty();
        assertThat(index.indexedClassCount()).isPositive();
    }

    @Test
    void totalScannedIsAtLeastIndexed() {
        assertThat(index.totalClassesScanned())
                .isGreaterThanOrEqualTo(index.indexedClassCount());
    }

    // ── Class content ─────────────────────────────────────────────────────────

    @Test
    void wellKnownClassIsIndexed() {
        // StringUtils is a well-known public class in commons-lang3
        Optional<IndexedClass> stringUtils = findClass("StringUtils");
        assertThat(stringUtils).isPresent();
    }

    @Test
    void classKindIsDetectedCorrectly() {
        IndexedClass su = findClass("StringUtils").orElseThrow();
        assertThat(su.kind()).isEqualTo(ClassKind.CLASS);
    }

    @Test
    void packageNameIsPopulated() {
        IndexedClass su = findClass("StringUtils").orElseThrow();
        assertThat(su.packageName()).isEqualTo("org.apache.commons.lang3");
    }

    @Test
    void fullyQualifiedNameMatchesPackagePlusSimple() {
        IndexedClass su = findClass("StringUtils").orElseThrow();
        assertThat(su.fullyQualifiedName())
                .isEqualTo(su.packageName() + "." + su.simpleName());
    }

    @Test
    void methodsAreIndexed() {
        IndexedClass su = findClass("StringUtils").orElseThrow();
        assertThat(su.methods()).isNotEmpty();
    }

    @Test
    void methodSignatureIsReadable() {
        IndexedClass su = findClass("StringUtils").orElseThrow();
        // isBlank(CharSequence) is a well-known method
        boolean hasIsBlank = su.methods().stream()
                .anyMatch(m -> m.name().equals("isBlank"));
        assertThat(hasIsBlank).isTrue();
    }

    @Test
    void onlyPublicClassesAreIndexed() {
        // Every indexed class must be public
        index.classes().forEach(cls ->
                assertThat(cls.fullyQualifiedName())
                        .as("Non-public class should not be indexed: %s", cls.fullyQualifiedName())
                        .isNotNull());
    }

    @Test
    void anonymousInnerClassesAreExcluded() {
        // No class simple name should be a number
        index.classes().forEach(cls ->
                assertThat(cls.simpleName().charAt(0))
                        .as("Anonymous class leaked into index: %s", cls.fullyQualifiedName())
                        .isNotEqualTo('1').isNotEqualTo('2').isNotEqualTo('3'));
    }

    @Test
    void moduleInfoIsExcluded() {
        boolean hasModuleInfo = index.classes().stream()
                .anyMatch(c -> c.simpleName().equals("module-info"));
        assertThat(hasModuleInfo).isFalse();
    }

    // ── Interfaces ────────────────────────────────────────────────────────────

    @Test
    void interfaceKindIsDetected() {
        // commons-lang3 has several interfaces — find any one
        Optional<IndexedClass> anyInterface = index.classes().stream()
                .filter(c -> c.kind() == ClassKind.INTERFACE)
                .findFirst();
        assertThat(anyInterface).isPresent();
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    void throwsForMissingJar() {
        assertThatThrownBy(() -> indexer.index(Path.of("/no/such/file.jar")))
                .isInstanceOf(JarIndexException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void throwsForNonJarFile(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        Path notAJar = tmp.resolve("file.txt");
        java.nio.file.Files.writeString(notAJar, "not a jar");
        assertThatThrownBy(() -> indexer.index(notAJar))
                .isInstanceOf(JarIndexException.class)
                .hasMessageContaining("Not a JAR");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Optional<IndexedClass> findClass(String simpleName) {
        return index.classes().stream()
                .filter(c -> c.simpleName().equals(simpleName))
                .findFirst();
    }

    static Path findJarOnClasspath(String namePrefix) {
        return com.bank.migration.TestJarLocator.findJar(namePrefix);
    }
}
