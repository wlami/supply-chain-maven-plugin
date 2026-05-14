package com.wlami.supplychain.mojo;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.CheckRegistry;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.ConsoleReporter;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.report.SarifReporter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class AbstractSupplyChainMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true) protected MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true) protected MavenSession session;

    @Parameter(property = "supply-chain.minReleaseAge", defaultValue = "P3D")
    protected String minReleaseAge;

    @Parameter protected List<String> minReleaseAgeExclusions;
    @Parameter protected List<String> repositoryAllowlist;
    @Parameter protected List<String> bannedDependencies;
    @Parameter protected String pgpKeysMap;
    @Parameter protected List<String> excludes;
    @Parameter protected List<com.wlami.supplychain.config.Override> overrides;
    @Parameter protected com.wlami.supplychain.config.ChecksConfig checks;
    @Parameter(defaultValue = "${project.build.directory}/supply-chain.sarif")
    protected String sarifOutput;
    @Parameter(defaultValue = "FAIL") protected String onViolation;
    @Parameter(defaultValue = "FAIL") protected String onNetworkError;

    protected PluginConfig buildConfig() {
        PluginConfig c = new PluginConfig();
        if (minReleaseAge != null) c.minReleaseAge = minReleaseAge;
        if (minReleaseAgeExclusions != null) c.minReleaseAgeExclusions = minReleaseAgeExclusions;
        if (repositoryAllowlist != null && !repositoryAllowlist.isEmpty()) c.repositoryAllowlist = repositoryAllowlist;
        if (bannedDependencies != null) c.bannedDependencies = bannedDependencies;
        if (pgpKeysMap != null) c.pgpKeysMap = pgpKeysMap;
        if (excludes != null) c.excludes = excludes;
        if (overrides != null) c.overrides = overrides;
        if (checks != null) c.checks = checks;
        if (sarifOutput != null) c.sarifOutput = sarifOutput;
        if (onViolation != null) c.onViolation = onViolation;
        if (onNetworkError != null) c.onNetworkError = onNetworkError;
        return c;
    }

    protected Findings runAllChecks(PluginConfig cfg) throws MojoExecutionException {
        Path cacheRoot = Paths.get(System.getProperty("user.home"), ".m2", "repository", ".supply-chain-cache");
        Findings agg = new Findings();
        try {
            List<Check> registered = CheckRegistry.defaults(cacheRoot);
            CheckContext ctx = new CheckContext(project, session, cfg, project.getArtifacts());
            for (Check c : registered) {
                if (!c.isEnabled(ctx)) continue;
                getLog().debug("Running check: " + c.id());
                agg.addAll(c.run(ctx));
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failure running supply-chain checks", e);
        }
        return agg;
    }

    protected void writeReports(Findings findings, PluginConfig cfg) {
        new ConsoleReporter(System.out).write(findings);
        if (cfg.sarifOutput != null && !cfg.sarifOutput.isEmpty()) {
            new SarifReporter(Paths.get(cfg.sarifOutput),
                "supply-chain-maven-plugin", pluginVersion()).write(findings);
        }
    }

    private String pluginVersion() {
        return getClass().getPackage().getImplementationVersion() != null
            ? getClass().getPackage().getImplementationVersion() : "0.1.0";
    }
}
