package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.repository.ArtifactRepository;
import java.util.HashSet;
import java.util.Set;

public final class RepositoryAllowlistCheck implements Check {

    @Override public String id() { return "repositoryAllowlist"; }
    @Override public String name() { return "Repository allowlist"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.repositoryAllowlist; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        if (ctx.project() == null) return findings;
        Set<String> allowed = new HashSet<>();
        for (String u : ctx.config().repositoryAllowlist) allowed.add(normalize(u));

        for (ArtifactRepository r : ctx.project().getRemoteArtifactRepositories()) {
            String norm = normalize(r.getUrl());
            if (!allowed.contains(norm)) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(r.getId())
                    .message("repository not on allowlist: " + r.getUrl())
                    .property("url", r.getUrl())
                    .build());
            }
        }
        return findings;
    }

    private static String normalize(String url) {
        if (url == null) return "";
        String s = url.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
