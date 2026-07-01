# ActivePivot Migration Assistant

An internal engineering tool that automates the migration of an ActivePivot Java codebase
using AI-assisted error fixing. It never guesses API replacements — every suggestion is
grounded in indexed JARs, curated knowledge documents, and prior successful fixes.

**230 tests · 0 failures · Java 21 · Spring Boot 3.5.3**

---

## How it works

```
mvn clean compile
       │
       ▼
  compiler error
       │
       ├── load source context (±100 lines, imports, method)
       ├── search knowledge docs (migration guides)
       ├── search JAR index (replacement API candidates)
       └── recall similar past fixes
                │
                ▼
          build AI prompt
                │
                ▼
         Claude API call
                │
          unified diff
                │
       show diff → approve?
                │
           apply to disk
                │
       mvn clean compile
                │
         record fix → loop
```

---

## Quick start

### Prerequisites

- Java 21+
- Maven 3.9+
- An Anthropic API key (optional — runs with `MOCK` provider by default)

### Build

```bash
mvn clean package -DskipTests
```

### 1. Prepare your data directories

```bash
mkdir -p ~/.migration-assistant/{jars,knowledge,jar-index,fixes}
```

Copy your ActivePivot JARs into `~/.migration-assistant/jars/`.  
Copy your migration guide docs (`.md` or `.txt`) into `~/.migration-assistant/knowledge/`.

### 2. Run

```bash
export MIGRATION_PROJECT_ROOT=/path/to/activepivot-project
export ANTHROPIC_API_KEY=sk-ant-...          # omit to use MOCK provider

java -jar target/active-pivot-migration-1.0.0-SNAPSHOT.jar \
  --migration.ai.provider=CLAUDE
```

### 3. Use the console

```
=== ActivePivot Migration Assistant ===
Type 'help' for available commands.

> scan          # index JARs in ~/.migration-assistant/jars/
> compile       # run mvn clean compile, show errors
> fix           # fix the current error with AI
> next          # advance to the next error
> history       # show similar past fixes
> status        # show session state
> help          # list all commands
> quit
```

---

## Architecture

```
src/main/java/com/bank/migration/
├── compiler/       # invoke mvn, parse [ERROR] output → CompilerError
├── source/         # load ±N lines around an error, detect enclosing method
├── knowledge/      # parse .md/.txt docs, score sections by term frequency
├── jar/            # reflect public API from JARs via URLClassLoader, persist as JSON
├── similarity/     # score indexed classes/methods against missing symbols
├── prompt/         # token-budget prompt assembly (CompactPromptBuilder)
├── ai/             # Anthropic Claude API client + MockAiProvider
├── fix/            # persist FixRecord JSON; few-shot retrieval for AI prompts
├── engine/         # full fix cycle: load → prompt → AI → apply → recompile
├── console/        # interactive REPL, ANSI diff display, session state
└── config/         # MigrationProperties — all tuneable parameters
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for sequence diagrams and design decisions.

---

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `migration.project-root` | `$PWD` | Path to the project being migrated |
| `migration.jars-dir` | `~/.migration-assistant/jars` | ActivePivot JARs to index |
| `migration.knowledge-dir` | `~/.migration-assistant/knowledge` | Migration guide docs |
| `migration.jar-index-dir` | `~/.migration-assistant/jar-index` | Generated JAR indexes |
| `migration.fixes-dir` | `~/.migration-assistant/fixes` | Persisted fix records |
| `migration.ai.provider` | `MOCK` | `MOCK` or `CLAUDE` |
| `migration.ai.api-key` | _(env)_ | Anthropic API key |
| `migration.ai.model` | `claude-sonnet-4-6` | Claude model ID |
| `migration.ai.max-input-tokens` | `8000` | Token budget per prompt |
| `migration.console.color-diff` | `true` | ANSI colour in diffs |

Full reference: [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md)

---

## Technology

| Concern | Choice |
|---------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.3 (CLI, no web) |
| Build | Maven |
| AI | Anthropic Claude (`java.net.http.HttpClient`, no SDK) |
| JSON | Jackson + JavaTimeModule |
| Tests | JUnit 5 + Mockito (230 tests) |
| Logging | SLF4J + Logback |

---

## Docs

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Component diagram, sequence diagrams, design decisions |
| [`docs/CONFIGURATION.md`](docs/CONFIGURATION.md) | Full property reference, production `application.yml` |
| [`docs/EXTENSION_GUIDE.md`](docs/EXTENSION_GUIDE.md) | How to add AI providers, knowledge formats, custom scorers |
| [`docs/FUTURE_IMPROVEMENTS.md`](docs/FUTURE_IMPROVEMENTS.md) | Prioritised backlog (auto-apply, batch mode, git integration) |

---

## Key design constraints

- **Never guesses ActivePivot API replacements.** If confidence is low, the AI responds
  `NEED_CONTEXT: <reason>` and the engine surfaces that to the user instead of applying a
  hallucinated diff.
- **No Docker, no database, no web server.** Single JAR, runs anywhere with Java 21 and Maven.
- **Testable without network or OS calls.** `ProcessRunner` and `ClaudeGateway` are
  `@FunctionalInterface` types swapped for lambdas in tests.
