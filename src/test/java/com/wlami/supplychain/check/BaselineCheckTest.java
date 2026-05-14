package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class BaselineCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void autoSkipsWhenFileMissing(@TempDir Path tmp) throws Exception {
        MavenProject p = new MavenProject();
        p.setFile(tmp.resolve("pom.xml").toFile());
        PluginConfig cfg = new PluginConfig();
        Findings f = new BaselineCheck().run(
            new CheckContext(p, null, cfg, Set.of(art("g", "a", "1"))));
        assertThat(f.all()).isEmpty();
    }

    @Test
    void flagsArtifactNotInBaseline(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve(".supply-chain-baseline.json");
        Files.writeString(baseline, "{\"artifacts\":[\"g:a:1\"]}");

        MavenProject p = new MavenProject();
        p.setFile(tmp.resolve("pom.xml").toFile());

        Findings f = new BaselineCheck().run(
            new CheckContext(p, null, new PluginConfig(),
                Set.of(art("g", "a", "1"), art("g", "b", "2"))));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).gav()).isEqualTo("g:b:2");
    }
}
