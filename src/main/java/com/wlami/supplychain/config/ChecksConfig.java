package com.wlami.supplychain.config;

public final class ChecksConfig {
    public boolean minReleaseAge = true;
    public boolean requireExactVersions = true;
    public boolean repositoryAllowlist = true;
    /** "auto" | "true" | "false" - auto = enforce when baseline file exists */
    public String baseline = "auto";
    /** "warn" | "fail" | "off" */
    public String checksumStrict = "warn";
    public boolean requireReleaseDeps = true;
    public boolean bannedDependencies = true;
    public boolean dependencyConvergence = true;
    public boolean pgpSignature = true;
}
