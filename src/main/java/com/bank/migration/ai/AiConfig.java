package com.bank.migration.ai;

import com.bank.migration.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Wires up the active {@link AiProvider} and the production {@link ClaudeGateway}.
 */
@Configuration
class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";

    /** Production HTTP gateway — single shared HttpClient instance. */
    @Bean
    ClaudeGateway claudeGateway(MigrationProperties props) {
        HttpClient httpClient = HttpClient.newHttpClient();
        return body -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_API_URL))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", props.ai().apiKey() != null ? props.ai().apiKey() : "")
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            return new ClaudeGateway.GatewayResponse(response.statusCode(), response.body());
        };
    }

    /**
     * Selects the active provider based on {@code migration.ai.provider}.
     * Defaults to MOCK so the tool works without an API key.
     */
    @Bean
    AiProvider aiProvider(MigrationProperties props,
                          MockAiProvider mock,
                          ClaudeAiProvider claude) {
        String providerName = props.ai().provider();
        AiProvider selected = "CLAUDE".equalsIgnoreCase(providerName) ? claude : mock;
        log.info("AI provider: {}", selected.providerName());
        return selected;
    }
}
