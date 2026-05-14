package com.wlami.supplychain.source;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class CentralSearchClientTest {

    private WireMockServer wm;

    @BeforeEach void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterEach void stop() { wm.stop(); }

    @Test
    void returnsTimestampWhenCentralResponds() {
        wm.stubFor(get(urlPathEqualTo("/solrsearch/select"))
            .willReturn(okJson("{\"response\":{\"docs\":[{\"timestamp\":1715000000000}]}}")));

        CentralSearchClient client = new CentralSearchClient("http://localhost:" + wm.port());
        Optional<Instant> ts = client.fetchReleaseDate("com.example", "foo", "1.0.0");
        assertThat(ts).isPresent();
        assertThat(ts.get().toEpochMilli()).isEqualTo(1715000000000L);
    }

    @Test
    void returnsEmptyWhenNoDocs() {
        wm.stubFor(get(urlPathEqualTo("/solrsearch/select"))
            .willReturn(okJson("{\"response\":{\"docs\":[]}}")));
        CentralSearchClient client = new CentralSearchClient("http://localhost:" + wm.port());
        assertThat(client.fetchReleaseDate("g", "a", "v")).isEmpty();
    }
}
