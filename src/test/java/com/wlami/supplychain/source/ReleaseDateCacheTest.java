package com.wlami.supplychain.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class ReleaseDateCacheTest {

    @Test
    void getReturnsValueWhenStored(@TempDir Path tmp) {
        ReleaseDateCache cache = new ReleaseDateCache(tmp.resolve("c.json"));
        cache.put("g:a:1", Instant.ofEpochMilli(123_456_789L));
        cache.flush();

        ReleaseDateCache reloaded = new ReleaseDateCache(tmp.resolve("c.json"));
        Optional<Instant> got = reloaded.get("g:a:1");
        assertThat(got).contains(Instant.ofEpochMilli(123_456_789L));
    }

    @Test
    void missingKeyReturnsEmpty(@TempDir Path tmp) {
        ReleaseDateCache cache = new ReleaseDateCache(tmp.resolve("c.json"));
        assertThat(cache.get("g:a:1")).isEmpty();
    }

    @Test
    void invalidateRemovesKey(@TempDir Path tmp) {
        ReleaseDateCache cache = new ReleaseDateCache(tmp.resolve("c.json"));
        cache.put("g:a:1", Instant.now());
        cache.invalidate("g:a:1");
        assertThat(cache.get("g:a:1")).isEmpty();
    }
}
