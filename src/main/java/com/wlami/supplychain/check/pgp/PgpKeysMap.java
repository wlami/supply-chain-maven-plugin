package com.wlami.supplychain.check.pgp;

import com.wlami.supplychain.config.GavPattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PgpKeysMap {

    public static final PgpKeysMap EMPTY = new PgpKeysMap(Collections.emptyList());

    public static final class Entry {
        final GavPattern pattern;
        final String fingerprint;
        Entry(GavPattern p, String fp) { this.pattern = p; this.fingerprint = fp; }
    }

    private final List<Entry> entries;
    private PgpKeysMap(List<Entry> entries) { this.entries = entries; }

    public static PgpKeysMap read(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String left = line.substring(0, eq).trim();
            String right = line.substring(eq + 1).trim();
            if (right.startsWith("0x")) right = right.substring(2);
            entries.add(new Entry(GavPattern.parse(left), right.toUpperCase()));
        }
        return new PgpKeysMap(entries);
    }

    public Optional<String> fingerprintFor(String g, String a, String v) {
        for (Entry e : entries) {
            if (e.pattern.matches(g, a, v)) return Optional.of(e.fingerprint);
        }
        return Optional.empty();
    }
}
