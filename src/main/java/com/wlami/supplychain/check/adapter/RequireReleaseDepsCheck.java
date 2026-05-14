package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;

public final class RequireReleaseDepsCheck implements Check {

    @Override public String id() { return "requireReleaseDeps"; }
    @Override public String name() { return "Require release dependencies"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.requireReleaseDeps; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        for (Artifact a : ctx.dependencies()) {
            if (a.isSnapshot()) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion())
                    .message("SNAPSHOT dependency not allowed")
                    .build());
            }
        }
        return findings;
    }
}
