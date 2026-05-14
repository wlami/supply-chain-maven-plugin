package com.wlami.supplychain.check.pgp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class KeyCache {
    private final Path root;

    public KeyCache(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
    }

    public Optional<byte[]> get(String fingerprint) throws IOException {
        Path f = root.resolve(fingerprint.toUpperCase() + ".asc");
        if (!Files.exists(f)) return Optional.empty();
        return Optional.of(Files.readAllBytes(f));
    }

    public void put(String fingerprint, byte[] bytes) throws IOException {
        Files.write(root.resolve(fingerprint.toUpperCase() + ".asc"), bytes);
    }
}
