package com.wlami.supplychain.config;

import java.util.ArrayList;
import java.util.List;

public final class PluginConfig {
    public String minReleaseAge = "P3D";
    /** GAV patterns whose min-release-age check is skipped entirely. */
    public List<String> minReleaseAgeExclusions = new ArrayList<>();
    public List<String> repositoryAllowlist = new ArrayList<>(List.of("https://repo.maven.apache.org/maven2"));
    public List<String> bannedDependencies = new ArrayList<>();
    public String pgpKeysMap;
    public List<String> excludes = new ArrayList<>();
    public List<Override> overrides = new ArrayList<>();
    public ChecksConfig checks = new ChecksConfig();
    public String sarifOutput;
    /** "FAIL" | "WARN" */
    public String onViolation = "FAIL";
    /** "FAIL" | "WARN" | "SKIP" */
    public String onNetworkError = "FAIL";
}
