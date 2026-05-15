package com.wlami.supplychain.config;

public final class ChecksConfig {
    public boolean minReleaseAge = true;
    public boolean requireExactVersions = true;
    public boolean repositoryAllowlist = true;
    /** "auto" | "true" | "false" - auto = enforce when baseline file exists */
    public String baseline = "auto";
    /** "warn" | "fail" | "off". Off by default because PGP signature verification (also default on)
     *  supersedes SHA1 checksum strict mode. Flip to "warn" or "fail" if you also want enforcement
     *  of Maven's transport-level checksum policy. */
    public String checksumStrict = "off";
    public boolean requireReleaseDeps = true;
    public boolean bannedDependencies = true;
    public boolean dependencyConvergence = true;
    public boolean pgpSignature = true;
}
