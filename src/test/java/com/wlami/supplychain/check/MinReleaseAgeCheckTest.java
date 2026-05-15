package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.source.ReleaseDateCache;
import com.wlami.supplychain.source.ReleaseDateSource;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class MinReleaseAgeCheckTest {

    private static Artifact artifact(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    private static ReleaseDateCache cache(Path tmp) {
        return new ReleaseDateCache(tmp.resolve("cache.json"));
    }

    static final class FakeSource implements ReleaseDateSource {
        final Map<String, Instant> data = new HashMap<>();
        final AtomicInteger calls = new AtomicInteger();
        FakeSource put(String g, String a, String v, Instant t) {
            data.put(g + ":" + a + ":" + v, t); return this;
        }
        @Override public Optional<Instant> fetchReleaseDate(String g, String a, String v) {
            calls.incrementAndGet();
            return Optional.ofNullable(data.get(g + ":" + a + ":" + v));
        }
    }

    @Test
    void flagsArtifactYoungerThanThreshold(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        FakeSource src = new FakeSource().put("com.example", "foo", "1.0.0", now.minusSeconds(86_400));

        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAge = "P3D";
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));

        Findings findings = new MinReleaseAgeCheck(src, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);

        assertThat(findings.all()).hasSize(1);
        assertThat(findings.all().get(0).gav()).isEqualTo("com.example:foo:1.0.0");
        assertThat(findings.all().get(0).message()).contains("P3D");
    }

    @Test
    void passesArtifactOlderThanThreshold(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        FakeSource src = new FakeSource().put("com.example", "foo", "1.0.0", now.minusSeconds(10 * 86_400));
        PluginConfig cfg = new PluginConfig();
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));

        Findings findings = new MinReleaseAgeCheck(src, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);
        assertThat(findings.all()).isEmpty();
    }

    @Test
    void overrideRelaxesAgeForMatchingArtifact(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        FakeSource src = new FakeSource().put("com.example", "internal", "1.0.0", now.minusSeconds(60));

        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAge = "P3D";
        com.wlami.supplychain.config.Override ov = new com.wlami.supplychain.config.Override();
        ov.pattern = "com.example:internal*"; ov.minReleaseAge = "P0D";
        cfg.overrides.add(ov);

        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "internal", "1.0.0")));
        Findings findings = new MinReleaseAgeCheck(src, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);
        assertThat(findings.all()).isEmpty();
    }

    @Test
    void exclusionListSkipsCheckEntirely(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        FakeSource src = new FakeSource();
        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAge = "P3D";
        cfg.minReleaseAgeExclusions = java.util.List.of("com.wlami:my-artifact");

        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.wlami", "my-artifact", "1.0.0")));
        Findings findings = new MinReleaseAgeCheck(src, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);
        assertThat(findings.all()).isEmpty();
        assertThat(src.calls.get()).isZero();
    }

    @Test
    void exclusionWildcardMatchesGroup(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        FakeSource src = new FakeSource();
        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAgeExclusions = java.util.List.of("com.wlami:*");

        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(
            artifact("com.wlami", "any-artifact", "1.0.0"),
            artifact("com.wlami", "other-artifact", "9.9.9")));
        Findings findings = new MinReleaseAgeCheck(src, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);
        assertThat(findings.all()).isEmpty();
        assertThat(src.calls.get()).isZero();
    }

    @Test
    void networkFailureEmitsWarningInsteadOfThrowing(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        ReleaseDateSource throwingSource = (g, a, v) -> { throw new RuntimeException("request timed out"); };
        PluginConfig cfg = new PluginConfig();
        // default onNetworkError = WARN
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));
        Findings f = new MinReleaseAgeCheck(throwingSource, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).severity()).isEqualTo(Severity.WARNING);
        assertThat(f.all().get(0).message()).contains("could not resolve release date").contains("timed out");
    }

    @Test
    void networkFailureWithFailModeEmitsError(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        ReleaseDateSource throwingSource = (g, a, v) -> { throw new RuntimeException("request timed out"); };
        PluginConfig cfg = new PluginConfig();
        cfg.onNetworkError = "FAIL";
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));
        Findings f = new MinReleaseAgeCheck(throwingSource, Clock.fixed(now, ZoneId.of("UTC")), cache(tmp)).run(ctx);
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void cacheHitSkipsSourceCall(@TempDir Path tmp) throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        ReleaseDateCache c = cache(tmp);
        c.put("com.example:foo:1.0.0", now.minusSeconds(10 * 86_400));
        FakeSource src = new FakeSource();

        PluginConfig cfg = new PluginConfig();
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));
        Findings f = new MinReleaseAgeCheck(src, Clock.fixed(now, ZoneId.of("UTC")), c).run(ctx);
        assertThat(f.all()).isEmpty();
        assertThat(src.calls.get()).isZero();
    }
}
