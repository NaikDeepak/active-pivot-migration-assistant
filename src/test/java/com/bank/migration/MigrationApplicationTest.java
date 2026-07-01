package com.bank.migration;

import com.bank.migration.console.ConsoleMigrationRunner;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "migration.project-root=/tmp",
        "migration.ai.provider=MOCK"
})
class MigrationApplicationTest {

    // Prevent the interactive REPL from blocking on System.in during context load tests.
    @MockBean
    ConsoleMigrationRunner consoleMigrationRunner;

    @Test
    void contextLoads() {
        // Verifies that the Spring context assembles without errors.
    }
}
