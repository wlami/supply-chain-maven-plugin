package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {
    @Test
    void fingerprintIsStableForSameGavAndCheck() {
        Finding a = Finding.builder()
            .checkId("minReleaseAge")
            .severity(Severity.ERROR)
            .gav("com.example:foo:1.0.0")
            .message("too young")
            .build();
        Finding b = Finding.builder()
            .checkId("minReleaseAge")
            .severity(Severity.ERROR)
            .gav("com.example:foo:1.0.0")
            .message("different message text")
            .build();
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }

    @Test
    void fingerprintDiffersByCheckId() {
        Finding a = Finding.builder().checkId("a").gav("g:a:1").severity(Severity.ERROR).message("m").build();
        Finding b = Finding.builder().checkId("b").gav("g:a:1").severity(Severity.ERROR).message("m").build();
        assertThat(a.fingerprint()).isNotEqualTo(b.fingerprint());
    }
}
