package com.bank.migration.console;

import com.bank.migration.config.MigrationProperties;
import org.springframework.stereotype.Component;

import java.io.PrintStream;

@Component
public class DiffPrinter {

    private static final String ANSI_RED   = "[31m";
    private static final String ANSI_GREEN = "[32m";
    private static final String ANSI_CYAN  = "[36m";
    private static final String ANSI_RESET = "[0m";

    private final MigrationProperties props;

    public DiffPrinter(MigrationProperties props) {
        this.props = props;
    }

    public void print(String diffPatch, PrintStream out) {
        if (diffPatch == null || diffPatch.isBlank()) {
            out.println("(no diff)");
            return;
        }
        boolean color = props.console().colorDiff();
        for (String line : diffPatch.split("\n", -1)) {
            if (color) {
                if (line.startsWith("+") && !line.startsWith("+++")) {
                    out.println(ANSI_GREEN + line + ANSI_RESET);
                } else if (line.startsWith("-") && !line.startsWith("---")) {
                    out.println(ANSI_RED + line + ANSI_RESET);
                } else if (line.startsWith("@@")) {
                    out.println(ANSI_CYAN + line + ANSI_RESET);
                } else {
                    out.println(line);
                }
            } else {
                out.println(line);
            }
        }
    }
}
