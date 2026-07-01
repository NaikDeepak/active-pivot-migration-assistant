# Extension Guide

This guide explains how to extend the assistant without modifying existing code.
Every extension point is a Spring-managed interface; you add a `@Component`
implementation and the existing wiring picks it up.

---

## 1. Adding a New AI Provider

**When:** You want to use a different LLM (GPT-4, Gemini, a local model, etc.).

**Interface:** `com.bank.migration.ai.AiProvider`

```java
public interface AiProvider {
    AiResponse complete(MigrationPrompt prompt);
    String providerName();            // used in logs and AiResponse
}
```

**Steps:**

1. Implement the interface:

```java
@Component
public class GptAiProvider implements AiProvider {

    private final MigrationProperties props;

    public GptAiProvider(MigrationProperties props) {
        this.props = props;
    }

    @Override
    public AiResponse complete(MigrationPrompt prompt) {
        // build OpenAI request body, call API, parse response
        // return AiResponse.parse(rawText, providerName(), inputTokens, outputTokens)
    }

    @Override
    public String providerName() { return "GPT"; }
}
```

2. Update `AiConfig.providerBean()` to recognise the new provider name, or add a
   `@ConditionalOnProperty` annotation to your implementation.

3. Set `migration.ai.provider: GPT` in `application.yml`.

**Note:** The transport layer (`ClaudeGateway`) is Claude-specific. For other providers,
write a separate gateway interface or call their SDK directly inside your `AiProvider`.

---

## 2. Adding a New Knowledge Document Format

**When:** Your migration guides are in HTML, AsciiDoc, or a proprietary format.

**Interface:** `com.bank.migration.knowledge.DocumentParser` is a `@Component`, not an
interface — but it is injectable. The easiest extension point is to pre-process your
format to `.md` or `.txt` and drop the result in `knowledge-dir`.

For full control, replace the parser:

1. Create a class that implements the same contract as `DocumentParser`:

```java
// same package, replaces the default bean
@Component
@Primary
public class HtmlDocumentParser {

    public List<KnowledgeSection> parse(Path file) {
        // parse HTML, produce KnowledgeSection records
    }
}
```

2. `FileSystemKnowledgeLoader.getSections()` calls `documentParser.parse(file)` for each
   file in `knowledge-dir`. Add your extension (e.g., `.html`) to the file filter in
   `FileSystemKnowledgeLoader.scanFiles()`.

---

## 3. Replacing the Fix History Backend

**When:** You want to store fix records in a database, S3, or a shared network store
instead of local JSON files.

**Interface:** `com.bank.migration.fix.FixHistory`

```java
public interface FixHistory {
    void record(FixRecord fix);
    List<FixRecord> findSimilar(CompilerError error, int topK);
}
```

**Steps:**

1. Implement the interface:

```java
@Component("postgresFixHistory")   // must be named — see below
public class PostgresFixHistory implements FixHistory {
    // ...
}
```

2. The default `FileSystemFixHistory` is annotated `@Component("fileSystemFixHistory")`.
   `NoOpFixHistory` is annotated `@ConditionalOnMissingBean(name = "fileSystemFixHistory")`.

   To activate your implementation and deactivate the filesystem one, either:
   - Add `@Primary` to your bean, or
   - Remove `@Component` from `FileSystemFixHistory` and add it to yours.

---

## 4. Customising Similarity Scoring

**When:** The default class/method name scoring doesn't match your naming conventions
(e.g., your project uses `Impl` suffix instead of `I` prefix).

**Component:** `com.bank.migration.similarity.CandidateScorer`

The scorer is a `@Component` with no interface. Override the scoring logic by:

1. Subclassing `CandidateScorer` and overriding `score()`, or
2. Creating a new `@Component @Primary class CustomCandidateScorer extends CandidateScorer`.

The key scoring knobs:
- `EXACT_MATCH = 100` — identical names
- `CASE_INSENSITIVE = 85` — same letters, different case
- `I_PREFIX_CONVENTION = 80` — candidate is `"I" + missingName` (ActivePivot pattern)
- `CANDIDATE_CONTAINS_MISSING = 65` — candidate name is a superset
- `CAMEL_TOKEN_OVERLAP` — overlap of camelCase token sets × 40

---

## 5. Customising the Prompt Format

**When:** You want a different system prompt, more sections, or a different AI output
format (e.g., full file replacement instead of unified diff).

**Interface:** `com.bank.migration.prompt.PromptBuilder`

```java
public interface PromptBuilder {
    MigrationPrompt build(PromptContext context);
}
```

Replace `CompactPromptBuilder` with a `@Primary` implementation. The system prompt's
`NEED_CONTEXT:` convention and unified diff format must match whatever `AiResponse.parse()`
expects, so update both together if you change the output format.

---

## 6. Replacing the Diff Applier

**When:** You want to apply fixes via the Language Server Protocol, a reformatter,
or a version-control-aware tool.

**Interface:** `com.bank.migration.engine.DiffApplier`

```java
public interface DiffApplier {
    ApplyResult apply(Path sourceFile, String diffPatch);
}
```

Implement and annotate with `@Primary`. The `DefaultMigrationEngine` injects this
interface by type, so your implementation will be used automatically.

---

## Testing Extensions

All interfaces are designed for Mockito injection. When writing tests for an extension:

```java
@ExtendWith(MockitoExtension.class)
class MyCustomProviderTest {

    @Mock MigrationProperties props;
    @InjectMocks MyCustomProvider provider;

    @Test
    void returnsNeedsContextOnEmptyResponse() {
        // ...
    }
}
```

The `MockAiProvider` in `ai/MockAiProvider.java` is a complete reference implementation
that shows the expected `AiResponse` construction patterns.
