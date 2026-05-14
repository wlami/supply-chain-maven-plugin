package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class DependencyConvergenceCheck implements Check {

    @Override public String id() { return "dependencyConvergence"; }
    @Override public String name() { return "Dependency convergence"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.dependencyConvergence; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        Map<String, Set<String>> versions = new HashMap<>();
        for (Artifact a : ctx.dependencies()) {
            String key = a.getGroupId() + ":" + a.getArtifactId();
            versions.computeIfAbsent(key, k -> new TreeSet<>()).add(a.getVersion());
        }
        for (Map.Entry<String, Set<String>> e : versions.entrySet()) {
            if (e.getValue().size() > 1) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(e.getKey())
                    .message("multiple versions resolved: " + e.getValue())
                    .build());
            }
        }
        return findings;
    }
}
