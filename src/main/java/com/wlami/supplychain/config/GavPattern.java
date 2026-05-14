package com.wlami.supplychain.config;

import java.util.regex.Pattern;

public final class GavPattern {

    private final Pattern group;
    private final Pattern artifact;
    private final Pattern version;

    private GavPattern(Pattern g, Pattern a, Pattern v) {
        this.group = g; this.artifact = a; this.version = v;
    }

    public static GavPattern parse(String raw) {
        if (raw == null || !raw.contains(":")) {
            throw new IllegalArgumentException("GAV pattern must contain at least one ':': " + raw);
        }
        String[] parts = raw.split(":", -1);
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("GAV pattern must be 'g:a' or 'g:a:v': " + raw);
        }
        return new GavPattern(
            toRegex(parts[0]),
            toRegex(parts[1]),
            parts.length == 3 ? toRegex(parts[2]) : null
        );
    }

    private static Pattern toRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') sb.append(".*");
            else if ("\\.[](){}+?|^$".indexOf(c) >= 0) sb.append('\\').append(c);
            else sb.append(c);
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }

    public boolean matches(String groupId, String artifactId, String versionId) {
        if (!group.matcher(groupId).matches()) return false;
        if (!artifact.matcher(artifactId).matches()) return false;
        if (version != null && !version.matcher(versionId).matches()) return false;
        return true;
    }
}
