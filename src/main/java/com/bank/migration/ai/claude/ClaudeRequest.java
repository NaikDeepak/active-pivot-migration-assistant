package com.bank.migration.ai.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Jackson model for the Anthropic Messages API request body. */
public record ClaudeRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<Message> messages
) {
    public record Message(String role, String content) {}
}
