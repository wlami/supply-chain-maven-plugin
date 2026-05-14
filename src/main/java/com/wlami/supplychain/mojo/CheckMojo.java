package com.wlami.supplychain.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = "check",
    defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public class CheckMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("supply-chain check (stub - no checks wired yet)");
    }
}
