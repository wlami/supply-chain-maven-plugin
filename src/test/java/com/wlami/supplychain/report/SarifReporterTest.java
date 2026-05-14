package com.wlami.supplychain.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class SarifReporterTest {

    @Test
    void writesValidSarifMatchingOfficialSchema(@TempDir Path tmp) throws Exception {
        Findings f = new Findings();
        f.add(Finding.builder()
            .checkId("minReleaseAge").severity(Severity.ERROR)
            .gav("com.example:foo:1.0.0")
            .message("too young")
            .property("publishedAt", "2026-05-13T12:00:00Z")
            .pomLine(42)
            .build());

        Path out = tmp.resolve("out.sarif");
        new SarifReporter(out, "supply-chain-maven-plugin", "0.1.0").write(f);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode written = mapper.readTree(Files.readAllBytes(out));
        assertThat(written.path("version").asText()).isEqualTo("2.1.0");
        assertThat(written.at("/runs/0/tool/driver/name").asText()).isEqualTo("supply-chain-maven-plugin");
        assertThat(written.at("/runs/0/results/0/ruleId").asText()).isEqualTo("minReleaseAge");

        try (InputStream schemaIn = getClass().getResourceAsStream("/sarif/sarif-2.1.0-schema.json")) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(schemaIn);
            Set<com.networknt.schema.ValidationMessage> errors = schema.validate(written);
            assertThat(errors).isEmpty();
        }
    }

    @Test
    void emptyFindingsWriteValidEmptyDoc(@TempDir Path tmp) throws Exception {
        Path out = tmp.resolve("empty.sarif");
        new SarifReporter(out, "supply-chain-maven-plugin", "0.1.0").write(new Findings());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode written = mapper.readTree(Files.readAllBytes(out));
        assertThat(written.at("/runs/0/results").size()).isZero();
    }
}
