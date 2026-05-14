package com.wlami.supplychain.source;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class CentralSearchClientTest {

    private HttpServer server;
    private String baseUrl;
    private volatile String responseBody;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/solrsearch/select", exchange -> {
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void returnsTimestampWhenCentralResponds() {
        responseBody = "{\"response\":{\"docs\":[{\"timestamp\":1715000000000}]}}";
        CentralSearchClient client = new CentralSearchClient(baseUrl);
        Optional<Instant> ts = client.fetchReleaseDate("com.example", "foo", "1.0.0");
        assertThat(ts).isPresent();
        assertThat(ts.get().toEpochMilli()).isEqualTo(1715000000000L);
    }

    @Test
    void returnsEmptyWhenNoDocs() {
        responseBody = "{\"response\":{\"docs\":[]}}";
        CentralSearchClient client = new CentralSearchClient(baseUrl);
        assertThat(client.fetchReleaseDate("g", "a", "v")).isEmpty();
    }
}
