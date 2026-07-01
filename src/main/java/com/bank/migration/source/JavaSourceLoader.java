package com.bank.migration.source;

import com.bank.migration.compiler.CompilerError;
import com.bank.migration.config.MigrationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Loads a compact, AI-ready source snippet for a given {@link CompilerError}.
 *
 * <p>Window calculation: ±{@code sourceSurroundingLines} lines around the error,
 * clamped to the actual file bounds.
 */
@Component
public class JavaSourceLoader implements SourceLoader {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceLoader.class);

    private static final Pattern CLASS_DECLARATION = Pattern.compile(
            ".*\\b(?:class|interface|enum|record|@interface)\\s+\\w+.*"
    );

    private final MigrationProperties props;
    private final ImportExtractor importExtractor;
    private final MethodDetector methodDetector;

    public JavaSourceLoader(
            MigrationProperties props,
            ImportExtractor importExtractor,
            MethodDetector methodDetector) {
        this.props = props;
        this.importExtractor = importExtractor;
        this.methodDetector = methodDetector;
    }

    @Override
    public SourceSnippet load(CompilerError error) {
        Path sourceFile = resolveSourceFile(error.sourceFile());
        log.debug("Loading source snippet from {} at line {}", sourceFile, error.line());

        List<String> allLines = readLines(sourceFile);

        List<String> imports = importExtractor.extract(allLines);
        String classDecl = extractClassDeclaration(allLines);

        int errorIdx = error.line() - 1;  // convert 1-based to 0-based
        String methodSig = methodDetector.detect(allLines, errorIdx);

        List<NumberedLine> window = buildWindow(allLines, errorIdx);

        log.debug("Snippet: {} imports, {} lines in window, method={}",
                imports.size(), window.size(), methodSig != null ? "detected" : "not found");

        return new SourceSnippet(sourceFile, imports, classDecl, methodSig, window, error.line());
    }

    private Path resolveSourceFile(Path declared) {
        if (Files.exists(declared)) {
            return declared;
        }
        // Compiler sometimes emits relative paths — try resolving against the project root
        Path resolved = props.projectRoot().resolve(declared);
        if (Files.exists(resolved)) {
            return resolved;
        }
        throw new SourceLoadException(
                "Source file not found: " + declared + " (also tried " + resolved + ")", null);
    }

    private List<String> readLines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new SourceLoadException("Cannot read source file: " + file, e);
        }
    }

    private String extractClassDeclaration(List<String> allLines) {
        return allLines.stream()
                .filter(l -> CLASS_DECLARATION.matcher(l).matches())
                .findFirst()
                .map(String::strip)
                .orElse(null);
    }

    private List<NumberedLine> buildWindow(List<String> allLines, int errorIdx) {
        int surrounding = props.sourceSurroundingLines();
        int startIdx = Math.max(0, errorIdx - surrounding);
        int endIdx   = Math.min(allLines.size(), errorIdx + surrounding + 1);  // exclusive

        List<NumberedLine> window = new ArrayList<>(endIdx - startIdx);
        for (int i = startIdx; i < endIdx; i++) {
            window.add(new NumberedLine(i + 1, allLines.get(i)));  // back to 1-based
        }
        return List.copyOf(window);
    }
}
