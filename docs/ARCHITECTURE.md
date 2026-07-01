# ActivePivot Migration Assistant — Architecture

## Overview

A Spring Boot CLI tool that drives iterative, AI-assisted migration of a Java codebase
from an older ActivePivot API to a newer one. It never guesses API replacements; every
suggestion is grounded in indexed JARs, curated knowledge documents, and prior successful
fixes.

```
┌──────────────────────────────────────────────────────────────┐
│                     ConsoleMigrationRunner                    │
│          (interactive REPL — compile / scan / fix …)         │
└──────┬──────────────┬──────────────────┬────────────────────-┘
       │              │                  │
       ▼              ▼                  ▼
 CompilerRunner  JarIndexer       MigrationEngine
 (mvn compile)  +JarIndexStore   (orchestrates one fix cycle)
                                        │
              ┌─────────────────────────┼──────────────────────┐
              │                         │                       │
              ▼                         ▼                       ▼
        SourceLoader             PromptBuilder             DiffApplier
        (±100 lines              (token-budget             (unified diff
         around error)            allocation)               apply to disk)
              │                         │                       │
              ▼                         ▼                       ▼
       KnowledgeLoader           AiProvider              CompilerRunner
       SimilaritySearch          (CLAUDE/MOCK)           (recompile after fix)
       FixHistory
```

---

## Package Responsibilities

| Package | Responsibility |
|---------|---------------|
| `compiler` | Invoke `mvn clean compile` via `ProcessBuilder`, parse `[ERROR]` output into typed `CompilerError` records |
| `source` | Load ±N lines around a compiler error; detect enclosing method; extract all imports |
| `knowledge` | Parse Markdown/TXT migration docs into scored sections; term-frequency matching |
| `jar` | Reflect public API from JAR files via `URLClassLoader`; persist indexes as JSON |
| `similarity` | Score indexed classes/methods against a missing symbol; return `CandidateMatch` list |
| `prompt` | Assemble all context into a token-budgeted `MigrationPrompt` |
| `ai` | Call the Anthropic Claude API (or a mock); parse unified diff or `NEED_CONTEXT:` prefix |
| `fix` | Persist and retrieve `FixRecord` JSON files for use as few-shot examples |
| `engine` | Orchestrate the full fix cycle: load → prompt → AI → approve → apply → recompile |
| `console` | Interactive REPL, session state, ANSI diff display |
| `config` | `MigrationProperties` record — all tunable parameters |

---

## Key Sequence Diagrams

### 1. Full Fix Cycle (`fix` command)

```
User          ConsoleMigrationRunner   MigrationEngine    AI
 │ fix              │                       │              │
 │─────────────────►│                       │              │
 │                  │ fixNextError()        │              │
 │                  │──────────────────────►│              │
 │                  │                       │ load source  │
 │                  │                       │──────────────│
 │                  │                       │ find knowledge/candidates/history
 │                  │                       │──────────────│
 │                  │                       │ build prompt │
 │                  │                       │──────────────│
 │                  │                       │ complete()   │
 │                  │                       │─────────────►│
 │                  │                       │◄─────────────│
 │                  │                       │ (diff or NEED_CONTEXT)
 │◄─────────────────│ show diff             │              │
 │ [y/N]            │                       │              │
 │─────────────────►│ apply(sourceFile, diff)              │
 │                  │──────────────────────►│              │
 │                  │                       │ recompile    │
 │                  │                       │ record fix   │
 │◄─────────────────│ outcome              │              │
```

### 2. JAR Scan (`scan` command)

```
User    ConsoleMigrationRunner   JarIndexStore   JarIndexer
 │ scan       │                       │               │
 │───────────►│ list *.jar in jarsDir │               │
 │            │──────────────────────►│               │
 │            │    isAlreadyIndexed?  │               │
 │            │◄──────────────────────│               │
 │            │ (if fresh, SKIP)      │               │
 │            │ (if stale/new)        │               │
 │            │ index(jar)            │               │
 │            │──────────────────────────────────────►│
 │            │◄──────────────────────────────────────│
 │            │  JarIndex             │               │
 │            │ save(index)           │               │
 │            │──────────────────────►│               │
 │◄───────────│ summary               │               │
```

### 3. Compile Cycle (`compile` command)

```
User    ConsoleMigrationRunner   CompilerRunner   MavenOutputParser
 │ compile    │                       │                  │
 │───────────►│ compile(projectRoot)  │                  │
 │            │──────────────────────►│ ProcessBuilder   │
 │            │                       │ mvn clean compile│
 │            │                       │──────────────────│
 │            │                       │ parse [ERROR] lines
 │            │                       │──────────────────►│
 │            │                       │◄──────────────────│
 │            │◄──────────────────────│ CompilationResult │
 │            │ updateCompilation()   │                  │
 │◄───────────│ show errors           │                  │
```

---

## Critical Design Decisions

### Functional Interfaces for Testability
`ProcessRunner` (wraps `ProcessBuilder`) and `ClaudeGateway` (wraps `HttpClient`) are
`@FunctionalInterface` types. Tests swap in lambdas without spawning OS processes or making
network calls. The real implementations are `@Bean`s in their respective `@Configuration` classes.

### JAR Reflection Without Transitive Dependencies
`ReflectionJarIndexer` uses a `URLClassLoader` scoped to one JAR. When a class fails to
load (e.g., because a dependency isn't on the classpath), `LinkageError` is caught per-class
and the class is counted as `skippedClasses` in the `JarIndex`. The rest of the JAR is still
indexed, so a few missing transitive deps don't block the entire scan.

### Token Budget Allocation
`CompactPromptBuilder` reserves `maxInputTokens - 300` overhead tokens for the system prompt
and response framing. Content sections are filled in priority order:

```
error info (always) → source snippet (always) → prior fixes (budget/2)
    → JAR candidates (remaining/2) → knowledge sections (remaining)
```

Each section is truncated with a note rather than cut mid-sentence.

### Sealed `FixOutcome`
The engine returns a `sealed interface FixOutcome` with five subtypes. The console switch
is exhaustive at compile time — adding a new outcome type forces all callers to handle it.

### Never-Guess Invariant
If `SimilaritySearch` returns nothing above `Confidence.INSUFFICIENT`, the AI receives no
candidate section. The system prompt instructs the AI to respond `NEED_CONTEXT: <reason>`
rather than hallucinating an API. The `MockAiProvider` mirrors this: if it cannot detect a
known symbol pattern, it returns `NEED_CONTEXT:` rather than a fake diff.

---

## Data Flow Summary

```
CompilerError
    │
    ├──► SourceLoader ──────────────────► SourceSnippet
    ├──► KnowledgeLoader ────────────────► List<KnowledgeMatch>
    ├──► SimilaritySearch ───────────────► List<CandidateMatch>
    └──► FixHistory ─────────────────────► List<FixRecord>
                                                │
                                         PromptContext
                                                │
                                         PromptBuilder
                                                │
                                         MigrationPrompt
                                                │
                                          AiProvider
                                                │
                                          AiResponse
                                         (diff or NEED_CONTEXT)
                                                │
                                         DiffApplier ──► modified .java file
                                                │
                                         CompilerRunner ──► CompilationResult
                                                │
                                          FixHistory.record(FixRecord)
```
