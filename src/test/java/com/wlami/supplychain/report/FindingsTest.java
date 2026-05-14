package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FindingsTest {
    @Test
    void emptyHasNoErrors() {
        Findings f = new Findings();
        assertThat(f.hasErrors()).isFalse();
        assertThat(f.all()).isEmpty();
    }

    @Test
    void addPreservesOrderAndDetectsErrors() {
        Findings f = new Findings();
        f.add(Finding.builder().checkId("c1").severity(Severity.WARNING).gav("g:a:1").message("m1").build());
        f.add(Finding.builder().checkId("c2").severity(Severity.ERROR).gav("g:b:1").message("m2").build());
        assertThat(f.hasErrors()).isTrue();
        assertThat(f.all()).hasSize(2);
        assertThat(f.errors()).hasSize(1);
    }
}
