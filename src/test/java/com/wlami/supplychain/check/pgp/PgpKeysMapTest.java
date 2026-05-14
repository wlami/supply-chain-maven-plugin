package com.wlami.supplychain.check.pgp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class PgpKeysMapTest {

    @Test
    void resolvesExactGavToFingerprint(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("keys.list");
        Files.writeString(f, "com.example:foo:1.0.0 = 0xABCDEF1234567890ABCDEF1234567890ABCDEF12\n");
        PgpKeysMap m = PgpKeysMap.read(f);
        Optional<String> fp = m.fingerprintFor("com.example", "foo", "1.0.0");
        assertThat(fp).contains("ABCDEF1234567890ABCDEF1234567890ABCDEF12");
    }

    @Test
    void supportsGlobPatterns(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("keys.list");
        Files.writeString(f, "com.example:* = 0x1111111111111111111111111111111111111111\n");
        PgpKeysMap m = PgpKeysMap.read(f);
        assertThat(m.fingerprintFor("com.example", "bar", "9.9.9")).contains("1111111111111111111111111111111111111111");
    }

    @Test
    void ignoresCommentsAndBlankLines(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("keys.list");
        Files.writeString(f, "# header\n\ng:a:v = 0xAAAA111111111111111111111111111111111111\n");
        PgpKeysMap m = PgpKeysMap.read(f);
        assertThat(m.fingerprintFor("g", "a", "v")).isPresent();
    }
}
