package com.bank.migration.ai;

import com.bank.migration.ai.claude.ClaudeRequest;
import com.bank.migration.ai.claude.ClaudeResponse;
import com.bank.migration.config.MigrationProperties;
import com.bank.migration.prompt.MigrationPrompt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * {@link AiProvider} that calls the Anthropic Messages API (claude-* models).
 *
 * <p>The actual HTTP transport is delegated to {@link ClaudeGateway} so this class
 * can be unit-tested without network access.
 *
 * <p>Requires {@code migration.ai.api-key} to be set when provider=CLAUDE.
 */
@Component
public class ClaudeAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAiProvider.class);

    private final MigrationProperties props;
    private final ObjectMapper objectMapper;
    private final ClaudeGateway gateway;

    public ClaudeAiProvider(
            MigrationProperties props,
            ObjectMapper objectMapper,
            ClaudeGateway gateway) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.gateway = gateway;
    }

    @Override
    public AiResponse complete(MigrationPrompt prompt) {
        validateApiKey();

        ClaudeRequest request = new ClaudeRequest(
                props.ai().model(),
                props.ai().maxOutputTokens(),
                prompt.systemPrompt(),
                List.of(new ClaudeRequest.Message("user", prompt.userPrompt()))
        );

        log.info("Calling Claude model: {} (~{} input tokens)",
                props.ai().model(), prompt.estimatedTokens());

        try {
            String requestBody = objectMapper.writeValueAsString(request);
            ClaudeGateway.GatewayResponse httpResponse = gateway.send(requestBody);

            if (!httpResponse.isSuccess()) {
                throw new AiException(
                        "Claude API returned HTTP " + httpResponse.statusCode()
                        + ": " + httpResponse.body());
            }

            ClaudeResponse response = objectMapper.readValue(httpResponse.body(), ClaudeResponse.class);
            String rawText = response.firstText();

            log.info("Claude responded: {} input tokens, {} output tokens",
                    response.usage().inputTokens(), response.usage().outputTokens());

            return AiResponse.parse(rawText, providerName(),
                    response.usage().inputTokens(),
                    response.usage().outputTokens());

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new AiException("Failed to call Claude API", e);
        }
    }

    @Override
    public String providerName() {
        return "CLAUDE";
    }

    private void validateApiKey() {
        String key = props.ai().apiKey();
        if (key == null || key.isBlank()) {
            throw new AiException(
                    "ANTHROPIC_API_KEY is not set. "
                    + "Set migration.ai.api-key or the ANTHROPIC_API_KEY env var.");
        }
    }
}
