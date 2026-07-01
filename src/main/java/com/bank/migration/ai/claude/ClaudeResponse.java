package com.bank.migration.ai.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Jackson model for the Anthropic Messages API response body. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeResponse(
        String id,
        List<ContentBlock> content,
        Usage usage
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("input_tokens")  int inputTokens,
            @JsonProperty("output_tokens") int outputTokens
    ) {}

    /** Returns the text of the first content block, or empty string if none. */
    public String firstText() {
        if (content == null || content.isEmpty()) return "";
        ContentBlock first = content.getFirst();
        return (first != null && first.text() != null) ? first.text() : "";
    }
}
