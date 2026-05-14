package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import java.io.PrintStream;

public final class ConsoleReporter implements Reporter {

    private final PrintStream out;

    public ConsoleReporter(PrintStream out) { this.out = out; }

    @Override
    public void write(Findings findings) {
        if (findings.all().isEmpty()) {
            out.println("[INFO] supply-chain check passed");
            return;
        }
        if (findings.hasErrors()) {
            out.println("[ERROR] supply-chain check failed:");
        } else {
            out.println("[WARNING] supply-chain check produced warnings:");
        }
        for (Finding f : findings.all()) {
            String prefix = f.severity() == Severity.ERROR ? "  [ERROR]"
                : f.severity() == Severity.WARNING ? "  [WARN] " : "  [NOTE] ";
            out.printf("%s [%s] %s %s%n", prefix, f.checkId(), f.gav(), f.message());
        }
        out.println("Run `mvn supply-chain:report` for full details without failing.");
    }
}
