package com.wlami.supplychain.check.pgp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class KeyCacheTest {

    @Test
    void roundTripsKeyBytes(@TempDir Path tmp) throws Exception {
        KeyCache c = new KeyCache(tmp);
        byte[] bytes = new byte[] {1, 2, 3, 4};
        c.put("ABCDEF12", bytes);
        Optional<byte[]> got = c.get("ABCDEF12");
        assertThat(got).isPresent();
        assertThat(got.get()).containsExactly(bytes);
    }

    @Test
    void missingKeyReturnsEmpty(@TempDir Path tmp) throws Exception {
        assertThat(new KeyCache(tmp).get("DEADBEEF")).isEmpty();
    }
}
