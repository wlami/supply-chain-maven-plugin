package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RepositoryAllowlistCheckTest {

    private static MavenProject projectWithRepos(String... urls) {
        MavenProject p = new MavenProject();
        java.util.List<org.apache.maven.artifact.repository.ArtifactRepository> repos = new java.util.ArrayList<>();
        for (String u : urls) {
            MavenArtifactRepository r = new MavenArtifactRepository();
            r.setId("r-" + u.hashCode());
            r.setUrl(u);
            r.setLayout(new org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout());
            repos.add(r);
        }
        p.setRemoteArtifactRepositories(repos);
        return p;
    }

    @Test
    void passesWhenAllReposAllowlisted() throws Exception {
        MavenProject p = projectWithRepos("https://repo.maven.apache.org/maven2");
        Findings f = new RepositoryAllowlistCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).isEmpty();
    }

    @Test
    void failsWhenUnknownRepoPresent() throws Exception {
        MavenProject p = projectWithRepos(
            "https://repo.maven.apache.org/maven2",
            "https://evil.example/repo");
        Findings f = new RepositoryAllowlistCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).message()).contains("evil.example");
    }
}
