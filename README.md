# supply-chain-maven-plugin

Single-plugin entry point for Maven supply-chain hardening.

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

## What it checks

| Check | Default | Description |
|---|---|---|
| `minReleaseAge` | `P3D` | Rejects artifacts published less than N days ago. |
| `requireExactVersions` | on | Rejects version ranges, `LATEST`, `RELEASE`. |
| `repositoryAllowlist` | Central only | Effective repositories must be on the allowlist. |
| `baseline` | auto | Enforces `.supply-chain-baseline.json` if present. |
| `checksumStrict` | warn | Warns when Maven is not in strict checksum mode. |
| `requireReleaseDeps` | on | Rejects SNAPSHOT dependencies. |
| `bannedDependencies` | empty list | User-supplied ban list. |
| `dependencyConvergence` | on | Flags multiple resolved versions of the same GA. |
| `pgpSignature` | on | Verifies `.asc` signatures against keyserver / pinned map. |

## Quick start

Add to your `pom.xml`:

```xml
<plugin>
  <groupId>com.wlami</groupId>
  <artifactId>supply-chain-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution><goals><goal>check</goal></goals></execution>
  </executions>
</plugin>
```

Empty configuration applies the full hardened defaults. Run `mvn validate` to execute.

## Excluding your own artifacts from `minReleaseAge`

Publishing your own artifact and want to consume it immediately without waiting three days? Add it to the dedicated exclusion list:

```xml
<configuration>
  <minReleaseAgeExclusions>
    <exclusion>com.wlami:my-artifact</exclusion>
  </minReleaseAgeExclusions>
</configuration>
```

Pin to `groupId:artifactId`. Avoid `groupId:*` - it matches future artifacts you have not published yet. Combine with `<repositoryAllowlist>` so the exempted artifact still has to come from a trusted repo.

## Goals

- `supply-chain:check` - run all enabled checks (bound to `validate`).
- `supply-chain:report` - run checks without failing the build.
- `supply-chain:dump-baseline` - write a baseline file of currently resolved deps.
- `supply-chain:refresh-cache` - clear the release-date cache.

## SARIF output

`target/supply-chain.sarif` is always produced. Upload to GitHub Code Scanning:

```yaml
- uses: github/codeql-action/upload-sarif@v3
  with:
    sarif_file: target/supply-chain.sarif
```

## Design and configuration reference

See `docs/superpowers/specs/2026-05-14-supply-chain-maven-plugin-design.md`.

## License

Apache-2.0.
