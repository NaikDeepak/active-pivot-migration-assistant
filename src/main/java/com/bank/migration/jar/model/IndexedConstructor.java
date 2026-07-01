package com.bank.migration.jar.model;

import java.util.List;

/**
 * A public or protected constructor extracted from a JAR class via reflection.
 */
public record IndexedConstructor(
        List<String> parameterTypes,
        List<String> annotationNames
) {

    public String signature(String simpleName) {
        String params = String.join(", ", parameterTypes.stream()
                .map(t -> t.contains(".") ? t.substring(t.lastIndexOf('.') + 1) : t)
                .toList());
        return simpleName + "(" + params + ")";
    }
}
