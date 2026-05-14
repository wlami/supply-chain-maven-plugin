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

## Releasing to Maven Central

Releases run on GitHub Actions via `.github/workflows/release.yml`. The workflow signs with PGP and publishes through the Sonatype Central Portal.

### Signing key

| | |
|---|---|
| Fingerprint | `84B3FFEC2085A4CBC92D0DF759E617E4BBD5963D` |
| Email | `mitzel@tawadi.de` |
| Stored as | GitHub Actions secrets `MAVEN_GPG_PRIVATE_KEY` + `MAVEN_GPG_PASSPHRASE` |
| Local backup | `~/Library/Application Support/wlami/supply-chain-mvn-plugin-signing.txt` (mode 600) |
| Uploaded to | `keys.openpgp.org` (verify email link to publish UID) |

### GitHub Actions secrets

| Secret | Purpose |
|---|---|
| `MAVEN_CENTRAL_USERNAME` | Sonatype Central Portal user token (username) |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype Central Portal user token (password) |
| `MAVEN_GPG_PRIVATE_KEY` | Armored private key for signing |
| `MAVEN_GPG_PASSPHRASE` | Passphrase for the key |

### Cutting a release

Two ways:

**A. Tag-driven** (recommended once `autoPublish=true`):

```bash
mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
git commit -am "release: 0.1.0"
git tag v0.1.0
git push --follow-tags origin main
```

**B. Manual dispatch:** trigger `Release` workflow in the Actions tab; pass `version=0.1.0`.

After the workflow finishes, the bundle is staged on Central Portal awaiting manual promotion (because `autoPublish=false` in `pom.xml`). Promote it in the Central Portal UI. Flip `autoPublish` to `true` once you trust the pipeline.

Then bump to the next snapshot:

```bash
mvn versions:set -DnewVersion=0.2.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "chore: bump to 0.2.0-SNAPSHOT"
git push
```

### Local release (fallback)

```bash
MAVEN_GPG_PASSPHRASE=$(cat ~/Library/Application\ Support/wlami/supply-chain-mvn-plugin-signing.txt | awk '/^Passphrase: / {print $2}') \
  mvn -Prelease clean deploy
```

Requires `~/.m2/settings.xml` with the Central Portal token under `<server id="central">`.

## Contributing

All changes land via pull request - `main` is the release-tracking branch and accepts merges from feature branches only. CI runs on every PR.

## License

Apache-2.0.
