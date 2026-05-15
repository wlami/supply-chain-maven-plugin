package com.wlami.supplychain;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import java.util.List;

/**
 * Auto-binds the {@code check} goal to the validate phase for any project that declares this plugin
 * with {@code <extensions>true</extensions>} and has no explicit {@code <executions>} block.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "supply-chain")
public class SupplyChainLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    private static final String OUR_GROUP = "com.wlami";
    private static final String OUR_ARTIFACT = "supply-chain-maven-plugin";

    @Override
    public void afterProjectsRead(MavenSession session) {
        for (MavenProject project : session.getProjects()) {
            injectIfMissing(project);
        }
    }

    private void injectIfMissing(MavenProject project) {
        if (project.getBuild() == null) return;
        for (Plugin p : project.getBuild().getPlugins()) {
            if (OUR_GROUP.equals(p.getGroupId()) && OUR_ARTIFACT.equals(p.getArtifactId())) {
                if (p.getExecutions().isEmpty()) {
                    PluginExecution exec = new PluginExecution();
                    exec.setId("default-supply-chain-check");
                    exec.setPhase("validate");
                    exec.setGoals(List.of("check"));
                    if (p.getConfiguration() != null) {
                        exec.setConfiguration(p.getConfiguration());
                    }
                    p.getExecutions().add(exec);
                }
                return;
            }
        }
    }
}
