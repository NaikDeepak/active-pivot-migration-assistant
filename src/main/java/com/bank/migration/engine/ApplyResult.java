package com.bank.migration.engine;

/**
 * Outcome of attempting to apply a unified diff patch to a source file.
 */
public record ApplyResult(boolean success, String message) {}
