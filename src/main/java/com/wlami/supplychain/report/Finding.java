package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class Finding {

    private final String checkId;
    private final Severity severity;
    private final String gav;
    private final String message;
    private final Map<String, String> properties;
    private final Integer pomLine;

    private Finding(Builder b) {
        this.checkId = Objects.requireNonNull(b.checkId);
        this.severity = Objects.requireNonNull(b.severity);
        this.gav = Objects.requireNonNull(b.gav);
        this.message = Objects.requireNonNull(b.message);
        this.properties = Map.copyOf(b.properties);
        this.pomLine = b.pomLine;
    }

    public String checkId() { return checkId; }
    public Severity severity() { return severity; }
    public String gav() { return gav; }
    public String message() { return message; }
    public Map<String, String> properties() { return properties; }
    public Integer pomLine() { return pomLine; }

    public String fingerprint() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest((gav + "|" + checkId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte v : digest) sb.append(String.format("%02x", v));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String checkId;
        private Severity severity;
        private String gav;
        private String message;
        private final Map<String, String> properties = new TreeMap<>();
        private Integer pomLine;

        public Builder checkId(String v) { this.checkId = v; return this; }
        public Builder severity(Severity v) { this.severity = v; return this; }
        public Builder gav(String v) { this.gav = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder property(String k, String v) { this.properties.put(k, v); return this; }
        public Builder pomLine(Integer v) { this.pomLine = v; return this; }
        public Finding build() { return new Finding(this); }
    }
}
