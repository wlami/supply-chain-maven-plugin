package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import java.util.Set;

public final class CheckContext {

    private final MavenProject project;
    private final MavenSession session;
    private final PluginConfig config;
    private final Set<Artifact> dependencies;

    public CheckContext(MavenProject project, MavenSession session, PluginConfig config, Set<Artifact> dependencies) {
        this.project = project;
        this.session = session;
        this.config = config;
        this.dependencies = dependencies;
    }

    public MavenProject project() { return project; }
    public MavenSession session() { return session; }
    public PluginConfig config() { return config; }
    public Set<Artifact> dependencies() { return dependencies; }
}
