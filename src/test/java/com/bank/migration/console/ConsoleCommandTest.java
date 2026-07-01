package com.bank.migration.console;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleCommandTest {

    @Test
    void parsesKnownKeywords() {
        assertThat(ConsoleCommand.parse("compile")).contains(ConsoleCommand.COMPILE);
        assertThat(ConsoleCommand.parse("scan"))   .contains(ConsoleCommand.SCAN);
        assertThat(ConsoleCommand.parse("fix"))    .contains(ConsoleCommand.FIX);
        assertThat(ConsoleCommand.parse("next"))   .contains(ConsoleCommand.NEXT);
        assertThat(ConsoleCommand.parse("history")).contains(ConsoleCommand.HISTORY);
        assertThat(ConsoleCommand.parse("status")) .contains(ConsoleCommand.STATUS);
        assertThat(ConsoleCommand.parse("help"))   .contains(ConsoleCommand.HELP);
        assertThat(ConsoleCommand.parse("quit"))   .contains(ConsoleCommand.QUIT);
    }

    @Test
    void exitAliasesQuit() {
        assertThat(ConsoleCommand.parse("exit")).contains(ConsoleCommand.QUIT);
    }

    @Test
    void parseIsCaseInsensitive() {
        assertThat(ConsoleCommand.parse("COMPILE")).contains(ConsoleCommand.COMPILE);
        assertThat(ConsoleCommand.parse("Scan"))   .contains(ConsoleCommand.SCAN);
    }

    @Test
    void parseStripsWhitespace() {
        assertThat(ConsoleCommand.parse("  compile  ")).contains(ConsoleCommand.COMPILE);
    }

    @Test
    void parseReturnsEmptyForUnknownInput() {
        assertThat(ConsoleCommand.parse("run")).isEmpty();
        assertThat(ConsoleCommand.parse("")).isEmpty();
        assertThat(ConsoleCommand.parse("   ")).isEmpty();
    }

    @Test
    void parseReturnsEmptyForNull() {
        assertThat(ConsoleCommand.parse(null)).isEmpty();
    }

    @Test
    void everyCommandHasKeywordAndDescription() {
        for (ConsoleCommand cmd : ConsoleCommand.values()) {
            assertThat(cmd.keyword).as(cmd.name() + ".keyword").isNotBlank();
            assertThat(cmd.description).as(cmd.name() + ".description").isNotBlank();
        }
    }
}
