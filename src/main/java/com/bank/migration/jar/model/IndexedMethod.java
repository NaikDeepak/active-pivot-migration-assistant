package com.bank.migration.jar.model;

import java.util.List;

/**
 * A public or protected method extracted from a JAR class via reflection.
 * All type names are fully-qualified so the AI can generate precise import statements.
 */
public record IndexedMethod(
        String name,
        String returnType,
        List<String> parameterTypes,
        List<String> annotationNames,
        boolean isStatic,
        boolean isAbstract
) {

    /** One-liner signature useful in prompts and search results. */
    public String signature() {
        String params = String.join(", ", parameterTypes.stream()
                .map(t -> t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t)
                .toList());
        return name + "(" + params + ")";
    }
}
