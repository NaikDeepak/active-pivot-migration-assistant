package com.bank.migration.ai;

import com.bank.migration.config.MigrationProperties;
import com.bank.migration.prompt.MigrationPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaudeAiProviderTest {

    private MigrationProperties props;
    private ObjectMapper mapper;
    private ClaudeGateway gateway;
    private ClaudeAiProvider provider;

    @BeforeEach
    void setUp() {
        props = mock(MigrationProperties.class);
        MigrationProperties.AiProperties ai = mock(MigrationProperties.AiProperties.class);
        when(ai.apiKey()).thenReturn("test-key");
        when(ai.model()).thenReturn("claude-sonnet-4-6");
        when(ai.maxOutputTokens()).thenReturn(2000);
        when(props.ai()).thenReturn(ai);

        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        gateway = mock(ClaudeGateway.class);
        provider = new ClaudeAiProvider(props, mapper, gateway);
    }

    private MigrationPrompt prompt() {
        return new MigrationPrompt("system", "user", 100);
    }

    private String successBody(String text) {
        return """
                {
                  "id": "msg_123",
                  "content": [{"type": "text", "text": "%s"}],
                  "usage": {"input_tokens": 100, "output_tokens": 50}
                }
                """.formatted(text.replace("\"", "\\\"").replace("\n", "\\n"));
    }

    @Test
    void providerNameIsClaude() {
        assertThat(provider.providerName()).isEqualTo("CLAUDE");
    }

    @Test
    void parsesSuccessfulDiffResponse() throws Exception {
        String diff = "--- a/Foo.java\\n+++ b/Foo.java\\n@@ -1 +1 @@\\n-old\\n+new";
        when(gateway.send(anyString()))
                .thenReturn(new ClaudeGateway.GatewayResponse(200, successBody(diff)));

        AiResponse response = provider.complete(prompt());

        assertThat(response.needsContext()).isFalse();
        assertThat(response.inputTokensUsed()).isEqualTo(100);
        assertThat(response.outputTokensUsed()).isEqualTo(50);
        assertThat(response.providerName()).isEqualTo("CLAUDE");
    }

    @Test
    void parsesNeedContextResponse() throws Exception {
        when(gateway.send(anyString()))
                .thenReturn(new ClaudeGateway.GatewayResponse(200,
                        successBody("NEED_CONTEXT: Missing replacement in JAR index")));

        AiResponse response = provider.complete(prompt());

        assertThat(response.needsContext()).isTrue();
        assertThat(response.contextRequest()).contains("Missing replacement");
    }

    @Test
    void throwsAiExceptionOnNon200Status() throws Exception {
        when(gateway.send(anyString()))
                .thenReturn(new ClaudeGateway.GatewayResponse(401,
                        "{\"error\": {\"message\": \"Invalid API key\"}}"));

        assertThatThrownBy(() -> provider.complete(prompt()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("401");
    }

    @Test
    void throwsAiExceptionWhenApiKeyMissing() {
        MigrationProperties.AiProperties ai = mock(MigrationProperties.AiProperties.class);
        when(ai.apiKey()).thenReturn("");
        when(props.ai()).thenReturn(ai);

        assertThatThrownBy(() -> provider.complete(prompt()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("api-key");
    }

    @Test
    void throwsAiExceptionOnNetworkError() throws Exception {
        when(gateway.send(anyString()))
                .thenThrow(new java.io.IOException("Connection refused"));

        assertThatThrownBy(() -> provider.complete(prompt()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("Failed to call Claude API");
    }
}
