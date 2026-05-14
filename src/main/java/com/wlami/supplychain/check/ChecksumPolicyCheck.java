package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.execution.MavenSession;

public final class ChecksumPolicyCheck implements Check {

    @Override public String id() { return "checksumStrict"; }
    @Override public String name() { return "Checksum strict mode"; }
    @Override public boolean isEnabled(CheckContext ctx) {
        return !"off".equalsIgnoreCase(ctx.config().checks.checksumStrict);
    }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        MavenSession session = ctx.session();
        if (session == null) return findings;
        String policy = session.getRequest() == null ? null : session.getRequest().getGlobalChecksumPolicy();
        if ("fail".equalsIgnoreCase(policy)) return findings;

        String mode = ctx.config().checks.checksumStrict;
        Severity sev = "fail".equalsIgnoreCase(mode) ? Severity.ERROR : Severity.WARNING;
        findings.add(Finding.builder()
            .checkId(id())
            .severity(sev)
            .gav("(maven settings)")
            .message("checksum policy not strict (current: " + policy + "). Use `-C` or settings.xml `<checksumPolicy>fail</checksumPolicy>`.")
            .build());
        return findings;
    }
}
