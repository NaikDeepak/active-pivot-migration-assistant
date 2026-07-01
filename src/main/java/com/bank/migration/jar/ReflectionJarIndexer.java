package com.bank.migration.jar;

import com.bank.migration.jar.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * {@link JarIndexer} that uses a {@link URLClassLoader} to reflect the public API of a JAR.
 *
 * <p>Classes that fail to load (e.g., because transitive dependencies are absent) are
 * silently skipped and counted in {@code skippedClasses}. This is expected for library JARs
 * without their full dependency tree — the classes that DO load are the ones we can offer as
 * replacement candidates.
 */
@Component
public class ReflectionJarIndexer implements JarIndexer {

    private static final Logger log = LoggerFactory.getLogger(ReflectionJarIndexer.class);

    private static final int LOG_PROGRESS_EVERY = 200;

    @Override
    public JarIndex index(Path jarFile) {
        validateJar(jarFile);
        log.info("Indexing JAR: {}", jarFile.getFileName());

        List<String> classNames = listIndexableClassNames(jarFile);
        log.info("Found {} candidate classes in {}", classNames.size(), jarFile.getFileName());

        List<IndexedClass> indexed   = new ArrayList<>();
        int skipped = 0;

        try (URLClassLoader loader = new URLClassLoader(
                new URL[]{jarFile.toUri().toURL()},
                getClass().getClassLoader())) {

            for (int i = 0; i < classNames.size(); i++) {
                if (i > 0 && i % LOG_PROGRESS_EVERY == 0) {
                    log.info("  Progress: {}/{} classes processed", i, classNames.size());
                }
                Optional<IndexedClass> cls = reflectClass(classNames.get(i), loader);
                if (cls.isPresent()) {
                    indexed.add(cls.get());
                } else {
                    skipped++;
                }
            }
        } catch (IOException e) {
            throw new JarIndexException("Cannot open JAR: " + jarFile, e);
        }

        log.info("Indexed {} classes ({} skipped) from {}", indexed.size(), skipped, jarFile.getFileName());

        try {
            return new JarIndex(
                    jarFile.getFileName().toString(),
                    jarFile.toAbsolutePath().toString(),
                    Files.size(jarFile),
                    Files.getLastModifiedTime(jarFile).toMillis(),
                    Instant.now().toString(),
                    classNames.size(),
                    skipped,
                    List.copyOf(indexed)
            );
        } catch (IOException e) {
            throw new JarIndexException("Cannot read JAR metadata: " + jarFile, e);
        }
    }

    // ── Class enumeration ─────────────────────────────────────────────────────

    private List<String> listIndexableClassNames(Path jarFile) {
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            jar.entries().asIterator().forEachRemaining(entry -> {
                if (isIndexableEntry(entry)) {
                    names.add(entryToClassName(entry.getName()));
                }
            });
        } catch (IOException e) {
            throw new JarIndexException("Cannot read JAR entries: " + jarFile, e);
        }
        return List.copyOf(names);
    }

    private boolean isIndexableEntry(JarEntry entry) {
        String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(".class")) return false;
        // Skip module and package descriptors
        String base = name.replace(".class", "");
        if (base.equals("module-info") || base.endsWith("/module-info")) return false;
        if (base.equals("package-info") || base.endsWith("/package-info")) return false;
        // Skip anonymous inner classes: Foo$1.class, Foo$2.class, etc.
        String simplePart = base.contains("$") ? base.substring(base.lastIndexOf('$') + 1) : base;
        return !simplePart.isEmpty() && !Character.isDigit(simplePart.charAt(0));
    }

    private String entryToClassName(String entryName) {
        return entryName.substring(0, entryName.length() - 6).replace('/', '.');
    }

    // ── Reflection ────────────────────────────────────────────────────────────

    private Optional<IndexedClass> reflectClass(String className, URLClassLoader loader) {
        try {
            Class<?> cls = loader.loadClass(className);

            if (!Modifier.isPublic(cls.getModifiers())) return Optional.empty();
            if (cls.isSynthetic()) return Optional.empty();

            String superName = (cls.getSuperclass() != null && cls.getSuperclass() != Object.class)
                    ? cls.getSuperclass().getName() : null;

            List<String> interfaces = Arrays.stream(cls.getInterfaces())
                    .map(Class::getName)
                    .toList();

            List<String> annotations = safeAnnotationNames(cls.getAnnotations());

            return Optional.of(new IndexedClass(
                    cls.getName(),
                    cls.getPackageName(),
                    cls.getSimpleName(),
                    determineKind(cls),
                    superName,
                    interfaces,
                    annotations,
                    reflectMethods(cls),
                    reflectConstructors(cls)
            ));
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Skipped class {}: {}", className, e.getMessage());
            return Optional.empty();
        }
    }

    private List<IndexedMethod> reflectMethods(Class<?> cls) {
        List<IndexedMethod> result = new ArrayList<>();
        try {
            for (Method m : cls.getDeclaredMethods()) {
                int mod = m.getModifiers();
                if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) continue;
                if (m.isSynthetic() || m.isBridge()) continue;
                try {
                    result.add(new IndexedMethod(
                            m.getName(),
                            m.getReturnType().getName(),
                            Arrays.stream(m.getParameterTypes()).map(Class::getName).toList(),
                            safeAnnotationNames(m.getAnnotations()),
                            Modifier.isStatic(mod),
                            Modifier.isAbstract(mod)
                    ));
                } catch (LinkageError e) {
                    log.debug("Skipped method {}.{}: {}", cls.getName(), m.getName(), e.getMessage());
                }
            }
        } catch (LinkageError e) {
            log.debug("Skipped all methods of {}: {}", cls.getName(), e.getMessage());
        }
        return List.copyOf(result);
    }

    private List<IndexedConstructor> reflectConstructors(Class<?> cls) {
        List<IndexedConstructor> result = new ArrayList<>();
        try {
            for (Constructor<?> c : cls.getDeclaredConstructors()) {
                if (!Modifier.isPublic(c.getModifiers()) && !Modifier.isProtected(c.getModifiers())) continue;
                if (c.isSynthetic()) continue;
                try {
                    result.add(new IndexedConstructor(
                            Arrays.stream(c.getParameterTypes()).map(Class::getName).toList(),
                            safeAnnotationNames(c.getAnnotations())
                    ));
                } catch (LinkageError e) {
                    log.debug("Skipped constructor in {}: {}", cls.getName(), e.getMessage());
                }
            }
        } catch (LinkageError e) {
            log.debug("Skipped all constructors of {}: {}", cls.getName(), e.getMessage());
        }
        return List.copyOf(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ClassKind determineKind(Class<?> cls) {
        if (cls.isAnnotation()) return ClassKind.ANNOTATION;
        if (cls.isEnum())       return ClassKind.ENUM;
        if (cls.isRecord())     return ClassKind.RECORD;
        if (cls.isInterface())  return ClassKind.INTERFACE;
        return ClassKind.CLASS;
    }

    private List<String> safeAnnotationNames(java.lang.annotation.Annotation[] annotations) {
        List<String> names = new ArrayList<>();
        for (var ann : annotations) {
            try {
                names.add(ann.annotationType().getSimpleName());
            } catch (Exception e) {
                // Annotation type not loadable — skip silently
            }
        }
        return List.copyOf(names);
    }

    private void validateJar(Path jarFile) {
        if (!Files.exists(jarFile)) {
            throw new JarIndexException("JAR file not found: " + jarFile, null);
        }
        if (!jarFile.getFileName().toString().endsWith(".jar")) {
            throw new JarIndexException("Not a JAR file: " + jarFile, null);
        }
    }
}
