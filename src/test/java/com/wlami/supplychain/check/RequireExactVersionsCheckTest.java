package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RequireExactVersionsCheckTest {

    private static Dependency dep(String g, String a, String v) {
        Dependency d = new Dependency();
        d.setGroupId(g); d.setArtifactId(a); d.setVersion(v);
        return d;
    }

    @Test
    void flagsVersionRange() throws Exception {
        MavenProject p = new MavenProject();
        p.setDependencies(List.of(dep("g", "a", "[1.0,2.0)")));
        Findings f = new RequireExactVersionsCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).message()).contains("[1.0,2.0)");
    }

    @Test
    void flagsLatestAndRelease() throws Exception {
        MavenProject p = new MavenProject();
        p.setDependencies(List.of(dep("g", "a", "LATEST"), dep("g", "b", "RELEASE")));
        Findings f = new RequireExactVersionsCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).hasSize(2);
    }

    @Test
    void passesExactVersion() throws Exception {
        MavenProject p = new MavenProject();
        p.setDependencies(List.of(dep("g", "a", "1.2.3")));
        Findings f = new RequireExactVersionsCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).isEmpty();
    }
}
