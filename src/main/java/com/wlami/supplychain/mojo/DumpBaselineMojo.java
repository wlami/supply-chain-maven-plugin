package com.wlami.supplychain.mojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wlami.supplychain.check.BaselineCheck;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

@Mojo(name = "dump-baseline", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class DumpBaselineMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        TreeSet<String> gavs = new TreeSet<>();
        for (Artifact a : project.getArtifacts()) {
            gavs.add(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion());
        }
        Path out = project.getBasedir().toPath().resolve(BaselineCheck.DEFAULT_FILENAME);
        try {
            ObjectMapper m = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Files.writeString(out, m.writeValueAsString(Map.of("artifacts", gavs)));
            getLog().info("Wrote baseline to " + out + " (" + gavs.size() + " artifacts)");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write baseline", e);
        }
    }
}
