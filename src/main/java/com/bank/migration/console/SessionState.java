package com.bank.migration.console;

import com.bank.migration.compiler.CompilationResult;
import com.bank.migration.compiler.CompilerError;

import java.util.Optional;

/**
 * Mutable state for one interactive console session.
 * Tracks the most recent compilation result and which error is currently focused.
 */
public class SessionState {

    private CompilationResult lastCompilation;
    private int currentErrorIndex;

    public void updateCompilation(CompilationResult result) {
        this.lastCompilation = result;
        this.currentErrorIndex = 0;
    }

    public boolean hasCompiled() {
        return lastCompilation != null;
    }

    public boolean hasErrors() {
        return lastCompilation != null && !lastCompilation.errors().isEmpty();
    }

    public int totalErrors() {
        return lastCompilation == null ? 0 : lastCompilation.errors().size();
    }

    public Optional<CompilerError> currentError() {
        if (!hasErrors() || currentErrorIndex >= lastCompilation.errors().size()) {
            return Optional.empty();
        }
        return Optional.of(lastCompilation.errors().get(currentErrorIndex));
    }

    public void nextError() {
        if (hasErrors() && currentErrorIndex < totalErrors() - 1) {
            currentErrorIndex++;
        }
    }

    public int currentErrorIndex() {
        return currentErrorIndex;
    }

    public CompilationResult lastCompilation() {
        return lastCompilation;
    }

    public String summary() {
        if (!hasCompiled()) return "No compilation run yet.";
        if (lastCompilation.success()) return "Last compile: SUCCESS";
        return String.format("Last compile: %d error(s), showing error %d/%d",
                totalErrors(), currentErrorIndex + 1, totalErrors());
    }
}
