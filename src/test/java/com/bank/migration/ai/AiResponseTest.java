package com.bank.migration.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiResponseTest {

    @Test
    void parsesNeedContextPrefix() {
        AiResponse r = AiResponse.parse(
                "NEED_CONTEXT: Missing JAR index for com.qfs package", "TEST", 100, 10);

        assertThat(r.needsContext()).isTrue();
        assertThat(r.contextRequest()).isEqualTo("Missing JAR index for com.qfs package");
        assertThat(r.diffPatch()).isNull();
    }

    @Test
    void parsesDiffResponse() {
        String diff = """
                --- a/Foo.java
                +++ b/Foo.java
                @@ -5,1 +5,1 @@
                -ActiveMonitor m = new ActiveMonitor();
                +IActiveMonitor m = new IActiveMonitorImpl();
                """;

        AiResponse r = AiResponse.parse(diff, "TEST", 200, 50);

        assertThat(r.needsContext()).isFalse();
        assertThat(r.diffPatch()).contains("---").contains("+++").contains("@@");
        assertThat(r.contextRequest()).isNull();
    }

    @Test
    void extractsDiffEvenWithLeadingPreamble() {
        String response = """
                Here is the fix:
                --- a/Foo.java
                +++ b/Foo.java
                @@ -1,1 +1,1 @@
                -old
                +new
                """;

        AiResponse r = AiResponse.parse(response, "TEST", 100, 20);

        assertThat(r.diffPatch()).startsWith("---");
    }

    @Test
    void recordsProviderName() {
        AiResponse r = AiResponse.parse("NEED_CONTEXT: x", "CLAUDE", 0, 0);
        assertThat(r.providerName()).isEqualTo("CLAUDE");
    }

    @Test
    void handlesNullInput() {
        AiResponse r = AiResponse.parse(null, "TEST", 0, 0);
        assertThat(r.needsContext()).isFalse();
        assertThat(r.diffPatch()).isNotNull();
    }

    @Test
    void needContextIsTrueOnlyForPrefix() {
        AiResponse r = AiResponse.parse(
                "The code needs context to work properly", "TEST", 0, 0);
        assertThat(r.needsContext()).isFalse();
    }
}
