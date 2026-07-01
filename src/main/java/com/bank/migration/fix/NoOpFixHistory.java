package com.bank.migration.fix;

import com.bank.migration.compiler.CompilerError;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder {@link FixHistory} that does nothing.
 * Active only when no real implementation (Phase 8) is on the classpath.
 */
@Component
@ConditionalOnMissingBean(name = "fileSystemFixHistory")
class NoOpFixHistory implements FixHistory {

    @Override
    public void record(FixRecord fix) { /* no-op until Phase 8 */ }

    @Override
    public List<FixRecord> findSimilar(CompilerError error, int topK) {
        return List.of();
    }
}
