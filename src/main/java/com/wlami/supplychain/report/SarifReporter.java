package com.wlami.supplychain.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.sarif.InputLocationResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SarifReporter implements Reporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path out;
    private final String toolName;
    private final String toolVersion;

    public SarifReporter(Path out, String toolName, String toolVersion) {
        this.out = out;
        this.toolName = toolName;
        this.toolVersion = toolVersion;
    }

    @Override
    public void write(Findings findings) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        doc.put("version", "2.1.0");

        Map<String, Object> run = new LinkedHashMap<>();
        Map<String, Object> tool = new LinkedHashMap<>();
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", toolName);
        driver.put("version", toolVersion);
        driver.put("informationUri", "https://github.com/wlami/supply-chain-maven-plugin");
        driver.put("rules", buildRules(findings));
        tool.put("driver", driver);
        run.put("tool", tool);

        run.put("results", buildResults(findings));
        doc.put("runs", List.of(run));

        try {
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.write(out, MAPPER.writeValueAsBytes(doc));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write SARIF: " + out, e);
        }
    }

    private List<Map<String, Object>> buildRules(Findings findings) {
        Map<String, Map<String, Object>> rules = new TreeMap<>();
        for (Finding f : findings.all()) {
            rules.computeIfAbsent(f.checkId(), id -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", id);
                r.put("name", id);
                r.put("shortDescription", Map.of("text", id));
                r.put("helpUri", "https://github.com/wlami/supply-chain-maven-plugin#" + id);
                return r;
            });
        }
        return new ArrayList<>(rules.values());
    }

    private List<Map<String, Object>> buildResults(Findings findings) {
        InputLocationResolver resolver = new InputLocationResolver();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Finding f : findings.all()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ruleId", f.checkId());
            r.put("level", level(f.severity()));
            r.put("message", Map.of("text", "[" + f.gav() + "] " + f.message()));
            r.put("locations", List.of(resolver.resolve(f, "pom.xml")));
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("gav", f.gav());
            props.putAll(f.properties());
            r.put("properties", props);
            r.put("partialFingerprints", Map.of("gavCheckId", f.fingerprint()));
            out.add(r);
        }
        return out;
    }

    private static String level(Severity s) {
        switch (s) {
            case ERROR: return "error";
            case WARNING: return "warning";
            default: return "note";
        }
    }
}
