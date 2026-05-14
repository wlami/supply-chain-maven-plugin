package com.wlami.supplychain.mojo;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = "check",
    defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public class CheckMojo extends AbstractSupplyChainMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        PluginConfig cfg = buildConfig();
        Findings findings = runAllChecks(cfg);
        writeReports(findings, cfg);
        if (findings.hasErrors() && "FAIL".equalsIgnoreCase(cfg.onViolation)) {
            throw new MojoFailureException("supply-chain check failed: " + findings.errors().size() + " error(s)");
        }
    }
}
