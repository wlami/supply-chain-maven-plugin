package com.wlami.supplychain.source;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {
    private HttpClientFactory() {}
    public static HttpClient create() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
}
