package com.wlami.supplychain.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class BaselineCheck implements Check {

    public static final String DEFAULT_FILENAME = ".supply-chain-baseline.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override public String id() { return "baseline"; }
    @Override public String name() { return "Dependency baseline"; }
    @Override public boolean isEnabled(CheckContext ctx) {
        String mode = ctx.config().checks.baseline;
        return "true".equalsIgnoreCase(mode) || "auto".equalsIgnoreCase(mode);
    }

    @Override
    public Findings run(CheckContext ctx) throws IOException {
        Findings findings = new Findings();
        if (ctx.project() == null || ctx.project().getFile() == null) return findings;

        Path baseline = ctx.project().getFile().toPath().getParent().resolve(DEFAULT_FILENAME);
        boolean explicit = "true".equalsIgnoreCase(ctx.config().checks.baseline);
        if (!Files.exists(baseline)) {
            if (explicit) {
                findings.add(Finding.builder()
                    .checkId(id()).severity(Severity.ERROR)
                    .gav("(baseline)").message("baseline file missing: " + baseline)
                    .build());
            }
            return findings;
        }

        Set<String> approved = readBaseline(baseline);
        for (Artifact a : ctx.dependencies()) {
            String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
            if (!approved.contains(gav)) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(gav)
                    .message("artifact not in baseline (run `mvn supply-chain:dump-baseline` to approve)")
                    .build());
            }
        }
        return findings;
    }

    static Set<String> readBaseline(Path file) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readAllBytes(file));
        Set<String> out = new HashSet<>();
        JsonNode arts = root.path("artifacts");
        if (arts.isArray()) for (JsonNode n : arts) out.add(n.asText());
        return out;
    }
}
