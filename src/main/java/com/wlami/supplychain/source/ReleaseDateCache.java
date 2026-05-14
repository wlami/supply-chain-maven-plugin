package com.wlami.supplychain.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ReleaseDateCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path file;
    private final Map<String, Long> data;

    public ReleaseDateCache(Path file) {
        this.file = file;
        this.data = load(file);
    }

    private static Map<String, Long> load(Path file) {
        try {
            if (!Files.exists(file)) return new ConcurrentHashMap<>();
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return new ConcurrentHashMap<>();
            Map<String, Long> read = MAPPER.readValue(bytes, new TypeReference<Map<String, Long>>() {});
            return new ConcurrentHashMap<>(read);
        } catch (IOException e) {
            return new ConcurrentHashMap<>();
        }
    }

    public Optional<Instant> get(String gav) {
        Long v = data.get(gav);
        return v == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(v));
    }

    public void put(String gav, Instant ts) { data.put(gav, ts.toEpochMilli()); }
    public void invalidate(String gav) { data.remove(gav); }
    public void clear() { data.clear(); }

    public void flush() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.write(file, MAPPER.writeValueAsBytes(new HashMap<>(data)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write cache: " + file, e);
        }
    }
}
