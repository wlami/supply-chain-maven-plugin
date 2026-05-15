package com.wlami.supplychain.check;

import com.wlami.supplychain.config.GavPattern;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.source.ReleaseDateCache;
import com.wlami.supplychain.source.ReleaseDateSource;
import org.apache.maven.artifact.Artifact;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MinReleaseAgeCheck implements Check {

    private final ReleaseDateSource client;
    private final Clock clock;
    private final ReleaseDateCache cache;

    public MinReleaseAgeCheck(ReleaseDateSource client, Clock clock, ReleaseDateCache cache) {
        this.client = client;
        this.clock = clock;
        this.cache = cache;
    }

    @Override public String id() { return "minReleaseAge"; }
    @Override public String name() { return "Minimum release age"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.minReleaseAge; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        String defaultMinRaw = ctx.config().minReleaseAge;
        Duration defaultMin = Duration.parse(defaultMinRaw);
        Instant now = clock.instant();

        List<GavPattern> exclusions = new ArrayList<>();
        for (String raw : ctx.config().minReleaseAgeExclusions) exclusions.add(GavPattern.parse(raw));

        String onError = ctx.config().onNetworkError;
        boolean cacheDirty = false;
        for (Artifact a : ctx.dependencies()) {
            if (matchesAny(exclusions, a)) continue;
            if ("pom".equalsIgnoreCase(a.getType())) continue;
            String[] minSpec = effectiveMin(ctx, a, defaultMinRaw);
            Duration min = Duration.parse(minSpec[1]);
            if (min.isZero() || min.isNegative()) continue;

            String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
            Optional<Instant> cached = cache.get(gav);
            Optional<Instant> published;
            if (cached.isPresent()) {
                published = cached;
            } else {
                try {
                    published = client.fetchReleaseDate(a.getGroupId(), a.getArtifactId(), a.getVersion());
                } catch (RuntimeException e) {
                    findings.add(networkErrorFinding(gav, e, onError));
                    continue;
                }
                if (published.isPresent()) { cache.put(gav, published.get()); cacheDirty = true; }
            }
            if (published.isEmpty()) continue;

            Duration age = Duration.between(published.get(), now);
            if (age.compareTo(min) < 0) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(gav)
                    .message(String.format("published %s (%s ago, threshold %s)",
                        published.get(), human(age), minSpec[1]))
                    .property("publishedAt", published.get().toString())
                    .property("threshold", minSpec[1])
                    .build());
            }
        }
        if (cacheDirty) cache.flush();
        return findings;
    }

    private static Finding networkErrorFinding(String gav, RuntimeException e, String onError) {
        Severity sev;
        switch (onError == null ? "WARN" : onError.toUpperCase()) {
            case "FAIL": sev = Severity.ERROR; break;
            case "SKIP": sev = Severity.NOTE; break;
            default:     sev = Severity.WARNING;
        }
        return Finding.builder()
            .checkId("minReleaseAge")
            .severity(sev)
            .gav(gav)
            .message("could not resolve release date (" + rootCauseMessage(e) + ")")
            .build();
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private static String[] effectiveMin(CheckContext ctx, Artifact a, String def) {
        for (com.wlami.supplychain.config.Override ov : ctx.config().overrides) {
            if (ov.minReleaseAge == null) continue;
            GavPattern p = ov.parsePattern();
            if (p.matches(a.getGroupId(), a.getArtifactId(), a.getVersion())) {
                return new String[] {"override", ov.minReleaseAge};
            }
        }
        return new String[] {"default", def};
    }

    private static boolean matchesAny(List<GavPattern> patterns, Artifact a) {
        for (GavPattern p : patterns) {
            if (p.matches(a.getGroupId(), a.getArtifactId(), a.getVersion())) return true;
        }
        return false;
    }

    private static String human(Duration d) {
        long days = d.toDays();
        if (days > 0) return days + "d";
        long hours = d.toHours();
        if (hours > 0) return hours + "h";
        return d.toMinutes() + "m";
    }
}
