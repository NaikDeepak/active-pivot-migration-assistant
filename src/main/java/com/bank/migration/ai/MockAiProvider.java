package com.bank.migration.ai;

import com.bank.migration.prompt.MigrationPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test/development {@link AiProvider} that returns a plausible but fake diff.
 *
 * <p>It inspects the prompt for a missing class or method name and constructs a minimal
 * unified diff that replaces the symbol with a TODO placeholder. This lets the full
 * pipeline run end-to-end without an API key.
 */
@Component
public class MockAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(MockAiProvider.class);

    private static final Pattern CLASS_PATTERN  = Pattern.compile("symbol:\\s+class\\s+(\\S+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("symbol:\\s+method\\s+(\\w+)");

    @Override
    public AiResponse complete(MigrationPrompt prompt) {
        log.info("[MOCK] Generating fake diff response");

        String userPrompt = prompt.userPrompt();
        String missing = detectMissing(userPrompt);

        if (missing == null) {
            String msg = "NEED_CONTEXT: Cannot determine missing symbol from the provided prompt.";
            return AiResponse.parse(msg, providerName(), 0, 0);
        }

        String diff = buildMockDiff(missing);
        log.debug("[MOCK] Returning diff for missing symbol: {}", missing);
        return AiResponse.parse(diff, providerName(), estimateTokens(prompt), diff.length() / 4);
    }

    @Override
    public String providerName() {
        return "MOCK";
    }

    private String detectMissing(String userPrompt) {
        Matcher classMatcher = CLASS_PATTERN.matcher(userPrompt);
        if (classMatcher.find()) return classMatcher.group(1);

        Matcher methodMatcher = METHOD_PATTERN.matcher(userPrompt);
        if (methodMatcher.find()) return methodMatcher.group(1);

        return null;
    }

    private String buildMockDiff(String missing) {
        return """
                --- a/Source.java
                +++ b/Source.java
                @@ -1,1 +1,1 @@
                -%s missingSymbol = new %s();
                +// TODO: Replace %s with the correct new ActivePivot API
                """.formatted(missing, missing, missing);
    }

    private int estimateTokens(MigrationPrompt prompt) {
        return (prompt.systemPrompt().length() + prompt.userPrompt().length()) / 4;
    }
}
