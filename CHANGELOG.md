# Changelog

All notable changes to this project will be documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/) and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] - 2026-05-14

Initial release.

### Added

- Single `supply-chain:check` mojo bundling nine supply-chain hardening checks. Empty config = full hardening.
- Checks:
  - `minReleaseAge` (`P3D` default) - rejects artifacts published more recently than the configured age.
  - `minReleaseAgeExclusions` - dedicated exclusion list for the common case of consuming your own freshly-published artifact.
  - `requireExactVersions` - rejects version ranges, `LATEST`, `RELEASE`.
  - `repositoryAllowlist` - effective repos must match the configured allowlist (Central only by default).
  - `baseline` - enforces `.supply-chain-baseline.json` if present; `dump-baseline` mojo writes it.
  - `checksumStrict` - warns if Maven is not in strict checksum mode.
  - `requireReleaseDeps` - rejects SNAPSHOT dependencies.
  - `bannedDependencies` - user-supplied ban list with glob patterns.
  - `dependencyConvergence` - flags multiple resolved versions of the same GA.
  - `pgpSignature` - downloads `.asc` from the artifact's remote repo and verifies via BouncyCastle against keys fetched from `keys.openpgp.org` (in-tree reimplementation; no `pgpverify-maven-plugin` dependency).
- Mojos: `check`, `report`, `dump-baseline`, `refresh-cache`.
- Reporters: console + SARIF 2.1.0 (schema-validated). SARIF written to `target/supply-chain.sarif` by default.
- Release-date cache at `~/.m2/repository/.supply-chain-cache/release-dates.json`; PGP key cache at `pgp-keys/`.

[Unreleased]: https://github.com/wlami/supply-chain-maven-plugin/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/wlami/supply-chain-maven-plugin/releases/tag/v0.1.0
