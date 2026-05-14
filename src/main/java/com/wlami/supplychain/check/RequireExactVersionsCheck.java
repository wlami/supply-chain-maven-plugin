package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.model.Dependency;
import java.util.regex.Pattern;

public final class RequireExactVersionsCheck implements Check {

    private static final Pattern RANGE = Pattern.compile("^[\\[(].*[\\])]$");

    @Override public String id() { return "requireExactVersions"; }
    @Override public String name() { return "Require exact dependency versions"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.requireExactVersions; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        if (ctx.project() == null) return findings;
        for (Dependency d : ctx.project().getDependencies()) {
            String v = d.getVersion();
            if (v == null) continue;
            String reason = problemFor(v);
            if (reason != null) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(d.getGroupId() + ":" + d.getArtifactId() + ":" + v)
                    .message(reason)
                    .build());
            }
        }
        return findings;
    }

    private static String problemFor(String v) {
        if (RANGE.matcher(v).matches()) return "version range not allowed: " + v;
        if ("LATEST".equals(v)) return "LATEST keyword not allowed";
        if ("RELEASE".equals(v)) return "RELEASE keyword not allowed";
        return null;
    }
}
