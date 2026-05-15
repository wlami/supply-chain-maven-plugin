# Changelog

All notable changes to this project will be documented here. Format loosely follows [Keep a Changelog](https://keepachangelog.com/) and the project follows [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.2.1] - 2026-05-15

### Fixed

- SARIF output now reports the actual plugin version (`${plugin.version}` Maven-injected). 0.2.0 still wrote `"version": "0.1.0"` because the hardcoded fallback always won.

## [0.2.0] - 2026-05-15

### Added

- Auto-bind: declaring the plugin with `<extensions>true</extensions>` now wires the `check` goal into the `validate` phase automatically. No more boilerplate `<executions>` block.

### Changed

- `checksumStrict` default flipped from `warn` to `off`. PGP signature verification (also default on) supersedes the SHA1 transport-level check, so the nag was redundant. Flip it back to `warn` / `fail` if you want both layers.
- `onNetworkError` default flipped from `FAIL` to `WARN`. A single slow / unreachable Central response no longer breaks consumer builds; it emits a per-artifact warning and processing continues.

### Fixed

- `MinReleaseAgeCheck` now catches per-artifact data-source failures (e.g. Central API timeouts) and emits a finding at the configured severity instead of throwing. Type-`pom` dependencies are skipped from the age check (we only care about resolved binary artifacts).

### Documented

- README: how to disable PGP signature verification globally when your dep graph has unsigned artifacts.

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

[Unreleased]: https://github.com/wlami/supply-chain-maven-plugin/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/wlami/supply-chain-maven-plugin/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/wlami/supply-chain-maven-plugin/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/wlami/supply-chain-maven-plugin/releases/tag/v0.1.0
