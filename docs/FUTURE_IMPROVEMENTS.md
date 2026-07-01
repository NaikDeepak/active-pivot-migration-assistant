# Future Improvements

Prioritised list of enhancements. Items at the top have the highest impact-to-effort ratio
for the ActivePivot migration use case.

---

## P1 — High Impact, Feasible Soon

### 1. Auto-Apply Mode
Skip the interactive approval prompt for high-confidence fixes. Use
`console.auto-apply-threshold` (already in config) to auto-apply when the AI's diff touches
only a single symbol and all JAR candidates have `Confidence.HIGH`. Roll back automatically
if recompile fails.

**Where to add:** `DefaultMigrationEngine.fixNextError()` — skip the `in.readLine()` call
when confidence exceeds the threshold.

### 2. Batch Fix Loop (`run` command)
Add a `run [maxErrors]` console command that iterates the full fix cycle — compile, fix,
recompile — until the build is clean or a maximum error count is reached. Stops and reports
on any `NEED_CONTEXT` or `ApplyFailed` outcome. This is the primary workflow for large-scale
migrations.

**Where to add:** New `runBatch()` method in `ConsoleMigrationRunner`; calls
`migrationEngine.fixNextError()` in a loop.

### 3. Knowledge Reload Command
Add a `reload` console command that calls `FileSystemKnowledgeLoader.reload()` without
restarting the application. Useful when adding new migration guides mid-session.

**Where to add:** New `ConsoleCommand.RELOAD` enum value; one-line handler in `runLoop`.

### 4. Git Integration
After each successful fix, commit the change with a structured message:

```
fix: [auto] cannot find symbol → IActivePivotManager (Foo.java:42)
```

Use `ProcessBuilder` (same pattern as `MavenCompilerRunner`) to call `git add <file>` and
`git commit`. Add an `engine.commitFixes` boolean property to gate this behaviour.

---

## P2 — Medium Impact

### 5. Multi-File Diff Support
`UnifiedDiffApplier` currently applies one file's worth of changes per diff. Some AI fixes
span two files (e.g., renaming a class requires updating both the declaration and its
callers). Parse multiple `--- a/` / `+++ b/` headers within one diff and apply each in turn.

**Where to add:** `UnifiedDiffApplier.apply()` — split on `--- a/` boundaries before
parsing hunks; resolve each target path against `projectRoot`.

### 6. Import Cleanup
After applying a diff that renames a class, the old import is often left behind, causing
a new `unused import` warning or a `cannot find symbol` on the old class. After each
successful fix, scan the modified file's imports and remove any that no longer resolve
against the JAR index.

### 7. Fix Deduplication
If the same `errorSignature` already has a `compilationSucceeded=true` fix record, skip the
AI call and re-apply the existing fix directly. This avoids redundant API calls for repeated
error patterns in large codebases.

**Where to add:** `DefaultMigrationEngine.fixNextError()` — check `fixHistory.findSimilar()`
before calling `aiProvider.complete()`.

### 8. Progress Reporting
Add a `[2/14 errors fixed]` counter to the compile output so engineers can track migration
progress across sessions. Persist a `migration-state.json` file in `fixes-dir` that records
how many errors were present at the start and how many have been resolved.

---

## P3 — Longer Term

### 9. Recursive Dependency JAR Discovery
Instead of requiring engineers to manually drop JARs into `jars-dir`, scan the project's
Maven dependencies via `mvn dependency:resolve -DincludeScope=compile` and auto-download
the required JARs. This removes the most common setup friction.

### 10. Confidence-Ranked Error Ordering
Currently errors are presented in parse order. Re-order them so that errors with HIGH-
confidence JAR candidates are fixed first. These are the easiest for the AI to get right,
building up the fix history faster and improving accuracy for the harder errors that follow.

### 11. Parallel Error Analysis
Run `SourceLoader`, `KnowledgeLoader`, and `SimilaritySearch` concurrently using
`CompletableFuture`. For large compilations with many errors, this makes the "gathering
context" step ~3× faster at the cost of some added complexity in `DefaultMigrationEngine`.

### 12. Session Persistence
Save `SessionState` to a JSON file so a migration can be paused and resumed across
application restarts. Store the last known compilation result and current error index.

### 13. Diff Preview with Line Numbers
Enhance `DiffPrinter` to show the actual source file line numbers in the `@@` hunk headers
in a more human-readable format (e.g., `Lines 42–46:`) alongside the standard unified diff.

---

## Known Limitations (not bugs, by design)

- **No transitive JAR indexing.** `ReflectionJarIndexer` skips classes whose dependencies
  are not on the classpath. Add transitive JARs to `jars-dir` if too many classes are
  `skippedClasses`.

- **Unified diff only.** The AI is instructed to return unified diffs. If the AI ignores
  this instruction and returns prose, `AiResponse.extractDiff()` will fail to find a diff
  and the fix is rejected. Use a higher-capability model (`claude-opus-4-8`) if this occurs
  frequently.

- **Single-file fixes only** (current implementation). See P2 item 5 for the multi-file fix
  roadmap.

- **No rollback on failed recompile.** If the AI's diff passes `DiffApplier` but the
  recompile fails, the source file remains in the modified state. Use `git checkout <file>`
  to revert. Git integration (P1 item 4) would automate this.
