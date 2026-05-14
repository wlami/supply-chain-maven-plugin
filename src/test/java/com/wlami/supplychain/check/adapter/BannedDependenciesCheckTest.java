package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class BannedDependenciesCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void flagsBannedGav() throws Exception {
        PluginConfig cfg = new PluginConfig();
        cfg.bannedDependencies = List.of("log4j:log4j");
        Findings f = new BannedDependenciesCheck().run(
            new CheckContext(null, null, cfg, Set.of(art("log4j", "log4j", "1.2.17"))));
        assertThat(f.all()).hasSize(1);
    }

    @Test
    void passesAllowedGav() throws Exception {
        PluginConfig cfg = new PluginConfig();
        cfg.bannedDependencies = List.of("log4j:log4j");
        Findings f = new BannedDependenciesCheck().run(
            new CheckContext(null, null, cfg, Set.of(art("org.slf4j", "slf4j-api", "2.0.0"))));
        assertThat(f.all()).isEmpty();
    }
}
