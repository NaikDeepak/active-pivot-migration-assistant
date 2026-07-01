# ActivePivot Migration Assistant

Internal engineering tool for automating the migration of an ActivePivot codebase.

## What it does

1. Runs `mvn clean compile` on your target project
2. Captures every compiler error
3. Picks the first unresolved error
4. Loads only the code needed to understand it (file + imports + method + 100 surrounding lines)
5. Loads relevant migration knowledge documents
6. Searches the indexed ActivePivot JAR for replacement APIs
7. Searches previously successful fixes
8. Builds a compact prompt and calls the configured AI provider
9. Shows a diff and asks you to approve or skip
10. Applies the fix and re-compiles
11. Loops until compilation succeeds

## Technology

| Concern | Choice |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.5 |
| Build | Maven |
| JSON | Jackson |
| Utilities | Apache Commons IO/Lang |
| Logging | SLF4J + Logback |
| Tests | JUnit 5 + Mockito |

## Quick start

### Prerequisites

- Java 21
- Maven 3.9+

### Build

```bash
mvn clean package -DskipTests
```

### Run

```bash
# Point at the project you want to migrate
export MIGRATION_PROJECT_ROOT=/path/to/activepivot-project
# Optional: real AI (Phase 8 onwards)
export ANTHROPIC_API_KEY=sk-ant-...

java -jar target/active-pivot-migration-1.0.0-SNAPSHOT.jar
```

Alternatively, override on the command line:

```bash
java -jar target/active-pivot-migration-1.0.0-SNAPSHOT.jar \
  --migration.project-root=/path/to/project \
  --migration.ai.provider=CLAUDE
```

## Data directories

All generated data lands under `~/.migration-assistant/` (configurable):

| Directory | Contents |
|---|---|
| `knowledge/` | Migration docs (Markdown / TXT) |
| `jar-index/` | JSON indexes of ActivePivot JARs |
| `fixes/` | Record of every successful fix applied |

Seed `knowledge/` with your migration documents before first run.

## Configuration reference

See `src/main/resources/application.yml`. Every key is overridable via
`--migration.<key>=<value>` on the command line or `MIGRATION_<KEY>` environment
variables (Spring Boot's standard relaxed binding rules apply).

## Build phases

| Phase | Description | Status |
|---|---|---|
| 1 | Project skeleton | ✅ |
| 2 | Compiler runner | pending |
| 3 | Source loader | pending |
| 4 | Knowledge loader | pending |
| 5 | JAR indexer | pending |
| 6 | Similarity search | pending |
| 7 | Prompt builder | pending |
| 8 | AI client | pending |
| 9 | Console application | pending |
| 10 | Migration engine | pending |
