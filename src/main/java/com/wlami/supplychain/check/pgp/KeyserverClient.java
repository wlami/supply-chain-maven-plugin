package com.wlami.supplychain.check.pgp;

import com.wlami.supplychain.source.HttpClientFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class KeyserverClient {

    private final String baseUrl;
    private final HttpClient http;

    public KeyserverClient(String baseUrl) { this(baseUrl, HttpClientFactory.create()); }
    public KeyserverClient(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
    }

    public byte[] fetchKey(String fingerprint) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/pks/lookup?op=get&options=mr&search=0x" + fingerprint);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Keyserver returned HTTP " + resp.statusCode() + " for " + fingerprint);
        }
        return resp.body();
    }
}
