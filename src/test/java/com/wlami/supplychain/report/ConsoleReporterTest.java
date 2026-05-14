package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.assertj.core.api.Assertions.assertThat;

class ConsoleReporterTest {
    @Test
    void writesGroupedSummaryWithSeverityPrefix() {
        Findings f = new Findings();
        f.add(Finding.builder().checkId("minReleaseAge")
            .severity(Severity.ERROR).gav("com.example:foo:1.0.0")
            .message("published 1 day ago, threshold P3D").build());
        f.add(Finding.builder().checkId("pgpSignature")
            .severity(Severity.ERROR).gav("com.example:bar:2.0.0")
            .message("missing .asc").build());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConsoleReporter(new PrintStream(out)).write(f);

        String text = out.toString();
        assertThat(text).contains("[minReleaseAge]");
        assertThat(text).contains("com.example:foo:1.0.0");
        assertThat(text).contains("[pgpSignature]");
        assertThat(text).contains("supply-chain check failed");
    }

    @Test
    void emptyFindingsWriteSuccessLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConsoleReporter(new PrintStream(out)).write(new Findings());
        assertThat(out.toString()).contains("supply-chain check passed");
    }
}
