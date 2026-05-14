package com.wlami.supplychain.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class CentralSearchClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private final HttpClient http;

    public CentralSearchClient(String baseUrl) {
        this(baseUrl, HttpClientFactory.create());
    }

    public CentralSearchClient(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
    }

    public Optional<Instant> fetchReleaseDate(String groupId, String artifactId, String version) {
        try {
            String q = URLEncoder.encode("g:" + groupId + " AND a:" + artifactId + " AND v:" + version,
                StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/solrsearch/select?q=" + q + "&core=gav&rows=1&wt=json");
            HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Central search HTTP " + resp.statusCode());
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode docs = root.path("response").path("docs");
            if (!docs.isArray() || docs.size() == 0) return Optional.empty();
            long ts = docs.get(0).path("timestamp").asLong(-1L);
            if (ts < 0) return Optional.empty();
            return Optional.of(Instant.ofEpochMilli(ts));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Central search failed for " + groupId + ":" + artifactId + ":" + version, e);
        }
    }
}
