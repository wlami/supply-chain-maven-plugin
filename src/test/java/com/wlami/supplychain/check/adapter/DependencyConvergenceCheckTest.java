package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class DependencyConvergenceCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void flagsDivergentVersions() throws Exception {
        Findings f = new DependencyConvergenceCheck().run(
            new CheckContext(null, null, new PluginConfig(),
                Set.of(art("g", "a", "1.0"), art("g", "a", "2.0"))));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).message()).contains("1.0").contains("2.0");
    }

    @Test
    void passesSingleVersion() throws Exception {
        Findings f = new DependencyConvergenceCheck().run(
            new CheckContext(null, null, new PluginConfig(),
                Set.of(art("g", "a", "1.0"), art("g", "b", "2.0"))));
        assertThat(f.all()).isEmpty();
    }
}
