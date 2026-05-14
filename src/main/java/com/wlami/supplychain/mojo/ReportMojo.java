package com.wlami.supplychain.mojo;

import com.wlami.supplychain.config.PluginConfig;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "report", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class ReportMojo extends AbstractSupplyChainMojo {

    @Override
    public void execute() throws MojoExecutionException {
        PluginConfig cfg = buildConfig();
        writeReports(runAllChecks(cfg), cfg);
    }
}
