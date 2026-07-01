package com.bank.migration.similarity;

import java.util.List;

/**
 * Structured representation of what the compiler cannot find, extracted from a {@link com.bank.migration.compiler.CompilerError}.
 *
 * <p>At least one of the three "missing" fields will be non-null for a useful query.
 */
public record SearchQuery(
        String missingClassName,         // e.g., "ActiveMonitor"       — from "symbol: class X"
        String missingMethodName,        // e.g., "computeRisk"         — from "symbol: method X(...)"
        List<String> missingMethodParams,// e.g., ["java.lang.String"]  — parameter types (may be empty)
        String missingPackage,           // e.g., "com.qfs.monitoring"  — from "package X does not exist"
        String locationClass             // e.g., "com.bank.RiskService" — where the error occurred
) {

    public boolean isClassSearch()   { return missingClassName  != null; }
    public boolean isMethodSearch()  { return missingMethodName != null; }
    public boolean isPackageSearch() { return missingPackage    != null; }

    public boolean isEmpty() {
        return !isClassSearch() && !isMethodSearch() && !isPackageSearch();
    }
}
