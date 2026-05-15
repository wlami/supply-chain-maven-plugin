package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChecksumPolicyCheckTest {

    static final class FakeSession extends MavenSession {
        private final MavenExecutionRequest req;
        FakeSession(MavenExecutionRequest req) {
            super(null, null, req, null);
            this.req = req;
        }
        @Override public MavenExecutionRequest getRequest() { return req; }
    }

    @Test
    void warnsWhenExplicitlyEnabledAndPolicyNotStrict() throws Exception {
        MavenExecutionRequest req = new DefaultMavenExecutionRequest();
        // no global checksum policy set
        MavenSession session = new FakeSession(req);

        PluginConfig cfg = new PluginConfig();
        cfg.checks.checksumStrict = "warn";
        Findings f = new ChecksumPolicyCheck().run(
            new CheckContext(null, session, cfg, java.util.Set.of()));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void offByDefaultMeansNoFindings() throws Exception {
        MavenExecutionRequest req = new DefaultMavenExecutionRequest();
        MavenSession session = new FakeSession(req);
        PluginConfig cfg = new PluginConfig();
        // default checksumStrict = "off"; ChecksumPolicyCheck.isEnabled returns false
        assertThat(new ChecksumPolicyCheck().isEnabled(
            new CheckContext(null, session, cfg, java.util.Set.of()))).isFalse();
    }

    @Test
    void passesWhenChecksumPolicyIsFail() throws Exception {
        MavenExecutionRequest req = new DefaultMavenExecutionRequest();
        req.setGlobalChecksumPolicy("fail");
        MavenSession session = new FakeSession(req);

        Findings f = new ChecksumPolicyCheck().run(
            new CheckContext(null, session, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).isEmpty();
    }
}
