package com.wlami.supplychain.config;

import java.time.Duration;

public final class Override {
    /** Plain string for Maven XML binding; parsed to GavPattern at use time. */
    public String pattern;
    /** ISO-8601 duration, e.g. "P0D" to disable for matched artifacts. */
    public String minReleaseAge;

    public GavPattern parsePattern() { return GavPattern.parse(pattern); }
    public Duration parseMinReleaseAge() { return minReleaseAge == null ? null : Duration.parse(minReleaseAge); }
}
