package com.bank.migration.ai;

import com.bank.migration.prompt.MigrationPrompt;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockAiProviderTest {

    private final MockAiProvider provider = new MockAiProvider();

    private MigrationPrompt promptWith(String userContent) {
        return new MigrationPrompt("system instructions", userContent, 100);
    }

    @Test
    void providerNameIsMock() {
        assertThat(provider.providerName()).isEqualTo("MOCK");
    }

    @Test
    void returnsDiffWhenClassSymbolPresent() {
        MigrationPrompt prompt = promptWith("""
                == COMPILER ERROR ==
                        symbol:   class ActiveMonitor
                """);

        AiResponse response = provider.complete(prompt);

        assertThat(response.needsContext()).isFalse();
        assertThat(response.diffPatch()).isNotBlank();
        assertThat(response.diffPatch()).contains("---").contains("+++");
    }

    @Test
    void diffContainsMissingSymbolName() {
        MigrationPrompt prompt = promptWith("symbol:   class ActiveMonitor\n");

        AiResponse response = provider.complete(prompt);

        assertThat(response.diffPatch()).contains("ActiveMonitor");
    }

    @Test
    void returnsNeedContextWhenNoSymbolDetected() {
        MigrationPrompt prompt = promptWith("';' expected at line 5");

        AiResponse response = provider.complete(prompt);

        assertThat(response.needsContext()).isTrue();
        assertThat(response.contextRequest()).isNotBlank();
    }

    @Test
    void returnsNeedContextWhenPromptIsEmpty() {
        MigrationPrompt prompt = promptWith("");

        AiResponse response = provider.complete(prompt);

        assertThat(response.needsContext()).isTrue();
    }

    @Test
    void detectsMethodSymbol() {
        MigrationPrompt prompt = promptWith("symbol:   method computeRisk(String)\n");

        AiResponse response = provider.complete(prompt);

        assertThat(response.needsContext()).isFalse();
        assertThat(response.diffPatch()).contains("computeRisk");
    }

    @Test
    void responseRecordsProviderName() {
        MigrationPrompt prompt = promptWith("symbol:   class Foo\n");
        AiResponse response = provider.complete(prompt);
        assertThat(response.providerName()).isEqualTo("MOCK");
    }
}
