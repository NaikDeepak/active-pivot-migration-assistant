package com.bank.migration;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

/** Shared test helper: locates JARs on the Maven Surefire test classpath. */
public final class TestJarLocator {

    private TestJarLocator() {}

    public static Path findJar(String namePrefix) {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .filter(p -> p.getFileName().toString().startsWith(namePrefix)
                          && p.getFileName().toString().endsWith(".jar"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        namePrefix + " JAR not found on test classpath"));
    }
}
