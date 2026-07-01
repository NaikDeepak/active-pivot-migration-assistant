package com.bank.migration.engine;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a standard unified diff (as produced by {@code diff -u} or git) to a source file.
 *
 * <p>Line numbers from the {@code @@} hunk header are used directly; context lines are kept
 * in-place without content verification so minor whitespace differences don't cause failures.
 * The file path in {@code ---}/{@code +++} headers is ignored — the caller supplies the target.
 */
@Component
public class UnifiedDiffApplier implements DiffApplier {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+\\d+(?:,\\d+)? @@");

    @Override
    public ApplyResult apply(Path sourceFile, String diffPatch) {
        if (!Files.isRegularFile(sourceFile)) {
            return new ApplyResult(false, "Source file not found: " + sourceFile);
        }

        List<String> sourceLines;
        try {
            sourceLines = new ArrayList<>(Files.readAllLines(sourceFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new ApplyResult(false, "Cannot read source file: " + e.getMessage());
        }

        List<Hunk> hunks;
        try {
            hunks = parseHunks(diffPatch);
        } catch (IllegalArgumentException e) {
            return new ApplyResult(false, "Invalid diff format: " + e.getMessage());
        }

        if (hunks.isEmpty()) {
            return new ApplyResult(false, "No hunks found in diff");
        }

        List<String> result;
        try {
            result = applyHunks(sourceLines, hunks);
        } catch (IndexOutOfBoundsException | IllegalStateException e) {
            return new ApplyResult(false, "Diff does not apply cleanly: " + e.getMessage());
        }

        try {
            Files.write(sourceFile, result, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new ApplyResult(false, "Cannot write source file: " + e.getMessage());
        }

        return new ApplyResult(true, "Applied " + hunks.size() + " hunk(s)");
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private List<Hunk> parseHunks(String diffPatch) {
        List<Hunk> hunks = new ArrayList<>();
        String[] rawLines = diffPatch.split("\n", -1);
        int i = 0;

        // Skip file header lines (--- and +++) and any leading blank lines
        while (i < rawLines.length) {
            String l = rawLines[i].stripTrailing();
            if (l.startsWith("---") || l.startsWith("+++") || l.isEmpty()) {
                i++;
            } else {
                break;
            }
        }

        while (i < rawLines.length) {
            String trimmed = rawLines[i].stripTrailing();
            Matcher m = HUNK_HEADER.matcher(trimmed);
            if (m.find()) {
                int oldStart = Integer.parseInt(m.group(1));
                List<HunkLine> hunkLines = new ArrayList<>();
                i++;
                while (i < rawLines.length) {
                    // Strip \r only; preserve leading space (context line marker).
                    String hl = rawLines[i].replace("\r", "");
                    // Next hunk header or next file header → stop
                    if (hl.startsWith("@@") || hl.startsWith("---") || hl.startsWith("+++")) {
                        break;
                    }
                    if (hl.startsWith("+")) {
                        hunkLines.add(new HunkLine(LineType.ADD, hl.substring(1)));
                    } else if (hl.startsWith("-")) {
                        hunkLines.add(new HunkLine(LineType.REMOVE, hl.substring(1)));
                    } else if (hl.startsWith(" ")) {
                        hunkLines.add(new HunkLine(LineType.CONTEXT, hl.substring(1)));
                    }
                    // Empty strings are trailing \n artifacts or "\ No newline" notices — skip.
                    i++;
                }
                hunks.add(new Hunk(oldStart, hunkLines));
            } else {
                i++;
            }
        }

        return hunks;
    }

    // ── Application ───────────────────────────────────────────────────────────

    private List<String> applyHunks(List<String> source, List<Hunk> hunks) {
        List<String> result = new ArrayList<>(source);
        int offset = 0; // net insertions/deletions from earlier hunks

        for (Hunk hunk : hunks) {
            int pos = hunk.oldStart() - 1 + offset; // 0-based position in result
            List<String> outputLines = new ArrayList<>();
            int currentPos = pos;

            for (HunkLine line : hunk.lines()) {
                switch (line.type()) {
                    case CONTEXT -> {
                        outputLines.add(result.get(currentPos));
                        currentPos++;
                    }
                    case REMOVE -> currentPos++;
                    case ADD    -> outputLines.add(line.content());
                }
            }

            int covered = currentPos - pos;
            result.subList(pos, currentPos).clear();
            result.addAll(pos, outputLines);
            offset += outputLines.size() - covered;
        }

        return result;
    }

    // ── Private types ─────────────────────────────────────────────────────────

    private enum LineType { CONTEXT, ADD, REMOVE }

    private record HunkLine(LineType type, String content) {}

    private record Hunk(int oldStart, List<HunkLine> lines) {}
}
