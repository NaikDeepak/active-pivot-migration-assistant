package com.bank.migration.fix;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileSystemFixHistoryTest {

    @TempDir
    Path fixesDir;

    private FileSystemFixHistory history;

    @BeforeEach
    void setUp() {
        MigrationProperties props = mock(MigrationProperties.class);
        when(props.fixesDir()).thenReturn(fixesDir);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        history = new FileSystemFixHistory(props, mapper);
    }

    private FixRecord fix(String signature, String errorMessage) {
        return new FixRecord(
                signature,
                "/proj/Foo.java",
                errorMessage,
                "symbol: class ActiveMonitor",
                "--- a/Foo.java\n+++ b/Foo.java\n@@ -1 +1 @@\n-old\n+new\n",
                "2026-07-02T10:00:00Z",
                true
        );
    }

    private CompilerError error(String signature, String message) {
        // signature format: "fileName:line:col:message"
        String[] parts = signature.split(":");
        return new CompilerError(
                Path.of(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), message, List.of());
    }

    @Test
    void recordPersistsJsonFile() {
        history.record(fix("Foo.java:42:5:cannot find symbol", "cannot find symbol"));

        assertThat(fixesDir.toFile().list()).hasSize(1);
        assertThat(fixesDir.toFile().list()[0]).endsWith(".json");
    }

    @Test
    void findSimilarReturnsEmptyWhenNoFixes() {
        CompilerError e = error("Bar.java:10:3:cannot find symbol", "cannot find symbol");
        assertThat(history.findSimilar(e)).isEmpty();
    }

    @Test
    void findSimilarMatchesOnExactSignature() {
        String sig = "Foo.java:42:5:cannot find symbol";
        history.record(fix(sig, "cannot find symbol"));

        CompilerError e = error(sig, "cannot find symbol");
        List<FixRecord> results = history.findSimilar(e);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().errorSignature()).isEqualTo(sig);
    }

    @Test
    void findSimilarMatchesByFileName() {
        history.record(fix("Foo.java:10:1:cannot find symbol", "cannot find symbol"));

        // Different line/col, same file → partial match
        CompilerError e = error("Foo.java:99:1:cannot find symbol", "cannot find symbol");
        List<FixRecord> results = history.findSimilar(e);

        assertThat(results).hasSize(1);
    }

    @Test
    void findSimilarReturnsTopK() {
        for (int i = 0; i < 5; i++) {
            history.record(fix("Foo.java:" + i + ":1:cannot find symbol", "cannot find symbol"));
        }

        CompilerError e = error("Foo.java:99:1:cannot find symbol", "cannot find symbol");
        List<FixRecord> results = history.findSimilar(e, 3);

        assertThat(results).hasSize(3);
    }

    @Test
    void exactSignatureRanksAbovePartialMatch() {
        String exactSig = "Foo.java:42:5:cannot find symbol";
        history.record(fix(exactSig, "cannot find symbol"));
        history.record(fix("Foo.java:1:1:cannot find symbol", "cannot find symbol"));

        CompilerError e = error(exactSig, "cannot find symbol");
        List<FixRecord> results = history.findSimilar(e, 5);

        // Exact signature match should come first
        assertThat(results.getFirst().errorSignature()).isEqualTo(exactSig);
    }

    @Test
    void recordMultipleFixesForSameError() {
        String sig = "Bar.java:5:3:cannot find symbol";
        history.record(fix(sig, "cannot find symbol"));
        history.record(fix(sig, "cannot find symbol"));

        assertThat(fixesDir.toFile().list()).hasSize(2);
    }

    @Test
    void returnsEmptyWhenDirectoryMissing() {
        MigrationProperties props = mock(MigrationProperties.class);
        when(props.fixesDir()).thenReturn(Path.of("/nonexistent/fixes"));
        FileSystemFixHistory h = new FileSystemFixHistory(props,
                new ObjectMapper().registerModule(new JavaTimeModule()));

        CompilerError e = error("Foo.java:1:1:cannot find symbol", "cannot find symbol");
        assertThat(h.findSimilar(e)).isEmpty();
    }
}
