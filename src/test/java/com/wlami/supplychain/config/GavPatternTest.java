package com.wlami.supplychain.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GavPatternTest {

    @Test
    void exactGavMatches() {
        GavPattern p = GavPattern.parse("com.example:foo:1.0.0");
        assertThat(p.matches("com.example", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "foo", "1.0.1")).isFalse();
    }

    @Test
    void wildcardArtifactMatches() {
        GavPattern p = GavPattern.parse("com.example:*");
        assertThat(p.matches("com.example", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "bar", "9.9.9")).isTrue();
        assertThat(p.matches("com.other", "foo", "1.0.0")).isFalse();
    }

    @Test
    void wildcardGroupSuffixMatches() {
        GavPattern p = GavPattern.parse("com.example.*:*");
        assertThat(p.matches("com.example.internal", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "foo", "1.0.0")).isFalse();
    }

    @Test
    void omittedVersionAllowsAny() {
        GavPattern p = GavPattern.parse("com.example:foo");
        assertThat(p.matches("com.example", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "foo", "2.0.0")).isTrue();
    }

    @Test
    void invalidPatternThrows() {
        assertThrows(IllegalArgumentException.class, () -> GavPattern.parse("no-colon"));
    }
}
