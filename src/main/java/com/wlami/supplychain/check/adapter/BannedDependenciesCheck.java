package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.config.GavPattern;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import java.util.ArrayList;
import java.util.List;

public final class BannedDependenciesCheck implements Check {

    @Override public String id() { return "bannedDependencies"; }
    @Override public String name() { return "Banned dependencies"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.bannedDependencies; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        List<GavPattern> bans = new ArrayList<>();
        for (String b : ctx.config().bannedDependencies) bans.add(GavPattern.parse(b));
        if (bans.isEmpty()) return findings;

        for (Artifact a : ctx.dependencies()) {
            for (GavPattern p : bans) {
                if (p.matches(a.getGroupId(), a.getArtifactId(), a.getVersion())) {
                    findings.add(Finding.builder()
                        .checkId(id())
                        .severity(Severity.ERROR)
                        .gav(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion())
                        .message("banned by configuration")
                        .build());
                    break;
                }
            }
        }
        return findings;
    }
}
