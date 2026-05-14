# supply-chain-maven-plugin - Design

**Date:** 2026-05-14
**Status:** Draft

## Motivation

Maven ecosystem has fragmented supply-chain defenses. Several mitigations exist but require knowing which plugins to add and how to configure them. Some defenses have no Maven equivalent at all (e.g. minimum release age, popularized by npm via `min-release-age`).

This plugin provides a single, opinionated entry point. One declaration in `pom.xml` activates a hardened set of checks with sane defaults. Wrapped third-party checks are invoked programmatically (no separate plugin executions); novel checks are implemented in-tree.

Reference article: ["What can a developer do against supply chain attacks when working with packages?" - Baust](https://www.linkedin.com/pulse/what-do-developer-against-supply-chain-attacks-when-working-baust-o57uf/)

## Goals

- Single plugin, single execution, unified report.
- Empty `<configuration/>` = full hardening.
- Cover every defense from the article that applies to Maven, plus a Maven-specific baseline mechanism.
- No silent magic: every active check is documented and toggleable.

## Non-Goals

- CVE scanning (use `owasp-dependency-check`).
- License compliance.
- SBOM generation.
- Auto-upgrades (use Renovate/Dependabot).

## Coordinates

| Field | Value |
|---|---|
| `groupId` | `com.wlami` |
| `artifactId` | `supply-chain-maven-plugin` |
| Initial version | `0.1.0` |
| Goal prefix | `supply-chain` |
| Default phase | `validate` |
| Java baseline | 11 |
| Maven baseline | 3.8+ |
| License | Apache-2.0 |

## What Maven already provides

| Aspect | Built-in? | Notes |
|---|---|---|
| SHA1/MD5 checksum | partial | Warns by default; strict only with `-C`. |
| PGP signature | no | Third-party (`pgpverify-maven-plugin`). |
| SNAPSHOT in release | partial | `maven-release-plugin` blocks; regular build allows. |
| Version ranges / LATEST / RELEASE | no | Resolved silently. |
| Repository allowlist | no | Mirrors restrict but opt-in. |
| Min release age | no | No native or third-party implementation. |
| Lockfile / first-seen baseline | no | None. |
| Allow/blocklist | partial | `enforcer:bannedDependencies`. |

## Checks

Each check is independently toggleable. Defaults are tuned for "sane hardening" - opting in with empty config produces the recommended posture.

### Own checks

| ID | Default | Purpose |
|---|---|---|
| `minReleaseAge` | `P3D` | Reject any dependency artifact whose Central publish timestamp is younger than the configured duration. 3 days follows the article's npm guidance: most supply-chain compromises are detected within hours; 3 days buys quiet time. |
| `requireExactVersions` | enabled | Reject version ranges (`[1.0,2.0)`, `(,1.0]`, etc.), `LATEST`, `RELEASE`. ~30 LoC walk over the resolved dep graph. No enforcer equivalent. |
| `repositoryAllowlist` | `[https://repo.maven.apache.org/maven2]` | Inspect the effective project + session repository list; fail if any active repository URL is not in the allowlist. |
| `baseline` | `auto` | If `.supply-chain-baseline.json` exists at project root: enforce. Any dep GAV not in baseline = fail. If absent: pass silently. `dump-baseline` mojo writes/refreshes the file. |
| `checksumStrict` | `warn` | Inspect Maven session for strict checksum mode (`-C` or settings). Default emits a warning if not set; can be flipped to `fail`. |

### Wrapped third-party checks (embedded)

Implemented by depending on `enforcer-rules` and invoking its rule classes directly inside our mojo (strategy A: embed). No separate plugin execution is added to the user's build.

| ID | Upstream | Default | Additional config |
|---|---|---|---|
| `requireReleaseDeps` | `enforcer-rules` | enabled, `failWhenParentIsSnapshot=true`, no excludes | optional `excludes` (GAV patterns) |
| `bannedDependencies` | `enforcer-rules` | enabled, empty ban list, `searchTransitive=true` | user-supplied ban list |
| `dependencyConvergence` | `enforcer-rules` | enabled, no excludes | optional `excludes` |

### PGP signature verification (own reimplementation)

Originally planned as a wrapper around `pgpverify-maven-plugin`. Its mojo classes are not designed as a library API; reimplementing avoids reflection and version skew.

| ID | Default | Additional config |
|---|---|---|
| `pgpSignature` | enabled, `failNoSignature=true`, `failNoKey=true`, `verifyPlugins=true`, `verifyPluginDependencies=true`, keyserver `hkps://keyserver.ubuntu.com` | optional `pgpKeysMap` for key pinning, optional `excludes` |

#### Why reimplement PGP verification

`pgpverify-maven-plugin` is not designed as a library API; its mojo classes are tightly coupled to Maven's plugin lifecycle. Reimplementing avoids reflection-based instantiation and version skew. The implementation: download `.asc` from the resolved artifact's remote, fetch the public key from the configured keyserver, verify with BouncyCastle. Expected size ~150 LoC. Trust model is "any valid signature whose key resolves on the keyserver"; with an optional `pgpKeysMap`, the user can pin specific keys per GAV.

## Configuration

Empty configuration = full hardening with defaults.

```xml
<plugin>
  <groupId>com.wlami</groupId>
  <artifactId>supply-chain-maven-plugin</artifactId>
  <version>0.1.0</version>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>

  <configuration>
    <minReleaseAge>P3D</minReleaseAge>

    <repositoryAllowlist>
      <repo>https://repo.maven.apache.org/maven2</repo>
    </repositoryAllowlist>

    <bannedDependencies>
      <!-- empty by default -->
      <!-- <ban>log4j:log4j</ban> -->
    </bannedDependencies>

    <pgpKeysMap>${project.basedir}/pgp-keys-map.list</pgpKeysMap>

    <overrides>
      <override>
        <pattern>com.example:internal-*</pattern>
        <minReleaseAge>P0D</minReleaseAge>
      </override>
    </overrides>

    <checks>
      <minReleaseAge>true</minReleaseAge>
      <requireExactVersions>true</requireExactVersions>
      <repositoryAllowlist>true</repositoryAllowlist>
      <baseline>auto</baseline>
      <checksumStrict>warn</checksumStrict>
      <requireReleaseDeps>true</requireReleaseDeps>
      <bannedDependencies>true</bannedDependencies>
      <dependencyConvergence>true</dependencyConvergence>
      <pgpSignature>true</pgpSignature>
    </checks>

    <sarifOutput>${project.build.directory}/supply-chain.sarif</sarifOutput>
  </configuration>
</plugin>
```

### Override semantics

`<overrides>` apply per-artifact. Each override matches a GAV pattern (`groupId:artifactId[:version]` with `*` wildcards) and can override:
- `minReleaseAge` (per-artifact age threshold, including `P0D` to disable)
- `excludes` for any wrapped check

## Mojos

| Goal | Purpose | Default phase |
|---|---|---|
| `check` | Run all enabled checks. Fail on first violation (unless `onViolation=warn`). | `validate` |
| `report` | Run all checks; print findings; never fail. | none (manual) |
| `dump-baseline` | Write `.supply-chain-baseline.json` with current resolved GAVs. | none (manual) |
| `refresh-cache` | Re-fetch all release dates from Central, ignoring cache. | none (manual) |

`onViolation` parameter: `FAIL` (default) | `WARN`.

## Data sources

### Maven Central Search API

Endpoint: `https://search.maven.org/solrsearch/select?q=g:GROUP+AND+a:ARTIFACT+AND+v:VERSION&core=gav&rows=1&wt=json`

Returns `timestamp` (epoch ms) per GAV. Used by `minReleaseAge`.

Fallback if non-Central repo: HEAD on the artifact URL, use `Last-Modified` header. Less precise but available for private/mirror repos.

### Release-date cache

- Path: `${user.home}/.m2/repository/.supply-chain-cache/release-dates.json`
- Format: `{"g:a:v": <epoch-ms>, ...}`
- TTL: hits cached indefinitely (releases are immutable on Central); misses cached 24h.
- `refresh-cache` mojo bypasses.

### Keyserver

- Endpoint: `hkps://keyserver.ubuntu.com` (configurable)
- Cache: `${user.home}/.m2/repository/.supply-chain-cache/pgp-keys/` (one file per key fingerprint)
- TTL: hits cached indefinitely; misses cached 1h with retry.

## Error handling

| Scenario | Behavior |
|---|---|
| Central API unreachable | `onNetworkError=FAIL` (default) | `WARN` | `SKIP`. Default fails to prevent silent bypass. |
| Cache hit but offline | Use cached value (immutable). |
| Cache miss + offline | Apply `onNetworkError` policy. |
| Keyserver unreachable | Apply `onNetworkError`. |
| Artifact has no `.asc` | If `pgpSignature.failNoSignature=true` (default), fail. |
| GAV not on Central (private repo) | Apply `repositoryAllowlist` first; if allowlisted, fall back to `Last-Modified` header for `minReleaseAge`. |

## Architecture

```
supply-chain-maven-plugin/
  pom.xml
  src/main/java/com/wlami/supplychain/
    mojo/
      CheckMojo.java              @Mojo(name="check", defaultPhase=VALIDATE)
      ReportMojo.java
      DumpBaselineMojo.java
      RefreshCacheMojo.java
    check/
      Check.java                  interface { String id(); Findings run(Context); }
      CheckRegistry.java
      MinReleaseAgeCheck.java
      RequireExactVersionsCheck.java
      RepositoryAllowlistCheck.java
      BaselineCheck.java
      ChecksumPolicyCheck.java
      adapter/
        RequireReleaseDepsCheck.java
        BannedDependenciesCheck.java
        DependencyConvergenceCheck.java
        PgpSignatureCheck.java    own reimpl; not an adapter strictly
    source/
      CentralSearchClient.java
      LastModifiedFallback.java
      ReleaseDateCache.java
      KeyserverClient.java
      KeyCache.java
    config/
      PluginConfig.java
      Override.java
      GavPattern.java
    report/
      Findings.java
      Finding.java                { check, severity, gav, message, location }
      Reporter.java               interface { write(Findings) }
      ConsoleReporter.java
      SarifReporter.java
      sarif/
        SarifModel.java           internal POJO tree mirroring SARIF 2.1.0 subset
        InputLocationResolver.java  resolves Maven InputLocation -> SARIF location
  src/test/java/com/wlami/supplychain/
    check/...                     unit tests per check with mock dep graph
  src/it/                         maven-invoker integration tests
    pass-default-config/
    fail-min-age/
    fail-unsigned/
    fail-banned/
    pass-baseline/
    fail-baseline-new-artifact/
```

## Dependencies (plugin POM)

- `org.apache.maven:maven-plugin-api`
- `org.apache.maven:maven-core`
- `org.apache.maven.plugin-tools:maven-plugin-annotations`
- `org.apache.maven.enforcer:enforcer-rules` - reused programmatically
- `org.bouncycastle:bcpg-jdk18on` - PGP verification
- `org.bouncycastle:bcprov-jdk18on`
- `com.fasterxml.jackson.core:jackson-databind` - SARIF JSON serialization (pinned to avoid version-skew with Maven core's transitive)
- Test: `junit-jupiter`, `assertj`, `wiremock`, `maven-invoker-plugin`, `com.networknt:json-schema-validator` (SARIF schema validation)

## Testing strategy

- **Unit:** each `Check` against a mock `Context` (synthetic dep graph + mock data sources).
- **Integration:** `maven-invoker-plugin` runs real Maven builds against fixture projects under `src/it/`. One fixture per pass case + one per fail case per check.
- **Network mocks:** Wiremock for Central Search and keyserver in unit tests; real Central in a single nightly smoke IT.
- **SARIF:** unit test `SarifReporter` output against the official SARIF 2.1.0 JSON schema using `json-schema-validator`. Integration tests assert that `target/supply-chain.sarif` exists, parses, validates, and contains the expected `results[]` entries.

## Reporting

Two reporters run in parallel on every `check` / `report` invocation:

### Console reporter

Grouped summary, severity-coloured:

```
[ERROR] supply-chain check failed:
  [minReleaseAge] com.example:foo:1.0.0 published 2026-05-13T12:00:00Z (1 day ago, threshold P3D)
  [pgpSignature] com.example:bar:2.0.0 missing .asc
  [bannedDependencies] log4j:log4j:1.2.17 (matched ban "log4j:log4j")
Run `mvn supply-chain:report` for full details without failing.
```

### SARIF reporter

SARIF 2.1.0 JSON written to `${project.build.directory}/supply-chain.sarif` (configurable; set to empty to disable). Output is consumable by GitHub Code Scanning, IntelliJ / VS Code SARIF viewers, and security dashboards.

**Structure:**

- `runs[0].tool.driver` - name `supply-chain-maven-plugin`, version, `rules[]` listing every check (id, name, shortDescription, fullDescription, helpUri pointing at plugin docs).
- `runs[0].results[]` - one entry per finding:
  - `ruleId` - check ID (`minReleaseAge`, `bannedDependencies`, ...)
  - `level` - `error` | `warning` | `note` mapped from severity
  - `message.text` - human-readable finding (same text as console)
  - `locations[]` - resolved from Maven's `InputLocation` API:
    - Direct deps: `physicalLocation` with `artifactLocation.uri` = relative path to declaring `pom.xml`, `region.startLine` = line of the `<dependency>` element
    - Transitive deps: file-level location on root `pom.xml` + `logicalLocations[].fullyQualifiedName` = the resolved transitive chain (e.g. `com.example:foo:1.0.0 -> com.example:bar:2.0.0`)
  - `properties` - bag with `gav`, `publishedAt` (where applicable), `severity`, `check` metadata
  - `partialFingerprints.gavCheckId` - stable hash `sha1(gav + checkId)` for de-dup across runs

**Configuration:**

```xml
<sarifOutput>${project.build.directory}/supply-chain.sarif</sarifOutput>
<!-- empty string disables SARIF -->
```

**Implementation notes:**

- JSON written via Jackson (already transitive through `maven-core`; explicit dependency added in plugin POM to avoid version-skew bugs).
- No external SARIF library: hand-rolled writer (~120 LoC) targeting only the subset of the schema we emit. Avoids dragging in unmaintained libs.
- Output file always written even on success (empty `results[]`), so CI/Code Scanning consumers always have a fresh artifact.
- File written before the mojo throws on `onViolation=FAIL`, so a failing build still produces the SARIF.

## Decisions

- License: Apache-2.0.
- Multi-module: `check` runs once per module (default behavior via `validate` phase binding). Aggregator mojo deferred to v0.2.
- Performance: parallelize Central API calls; bound to 8 concurrent requests by default.

## Out of scope for v0.1.0

- HTML reporter
- Aggregator mojo for multi-module
- Custom keyserver caching strategies
- Auto-update of baseline on minor version bumps
- Integration with Sonatype's deprecated `oss-index`
