package com.bank.migration.jar.model;

import java.util.List;

/**
 * A single public class/interface/enum/annotation indexed from a JAR.
 *
 * <p>All names are fully-qualified to eliminate ambiguity when presenting candidates to the AI.
 */
public record IndexedClass(
        String fullyQualifiedName,
        String packageName,
        String simpleName,
        ClassKind kind,
        String superClassName,           // null when superclass is java.lang.Object or not available
        List<String> interfaceNames,
        List<String> annotationNames,
        List<IndexedMethod> methods,
        List<IndexedConstructor> constructors
) {

    /** Returns true if this class could be a replacement for a class with the given simple name. */
    public boolean mightReplace(String candidateSimpleName) {
        return simpleName.equalsIgnoreCase(candidateSimpleName)
                || simpleName.contains(candidateSimpleName)
                || candidateSimpleName.contains(simpleName);
    }
}
