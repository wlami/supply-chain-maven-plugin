# supply-chain-maven-plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Maven plugin that bundles supply-chain hardening checks (min release age, repository allowlist, baseline lockfile, version pinning, banned deps, dependency convergence, PGP signatures, checksum strictness) into a single execution with sane defaults and SARIF + console reporting.

**Architecture:** Single Maven plugin with `check`, `report`, `dump-baseline`, `refresh-cache` mojos. Checks implement a common `Check` interface and are discovered through a `CheckRegistry`. Wrapped third-party checks invoke `enforcer-rules` classes programmatically (strategy A: embed). PGP verification is reimplemented in-tree with BouncyCastle. Findings flow through a `Reporter` abstraction with `ConsoleReporter` and `SarifReporter` implementations.

**Tech Stack:**
- Java 11 baseline
- Maven 3.8+ build + runtime target
- `maven-plugin-api`, `maven-core`, `maven-plugin-annotations`
- `enforcer-rules` (embedded use)
- `bcpg-jdk18on`, `bcprov-jdk18on` (PGP)
- `jackson-databind` (SARIF)
- Test: JUnit Jupiter, AssertJ, WireMock, `maven-invoker-plugin`, `json-schema-validator`

**Spec:** `docs/superpowers/specs/2026-05-14-supply-chain-maven-plugin-design.md`

**Coordinates:**
- `groupId`: `com.wlami`
- `artifactId`: `supply-chain-maven-plugin`
- Initial version: `0.1.0-SNAPSHOT`
- Goal prefix: `supply-chain`

---

## File Structure

```
supply-chain-maven-plugin/
├── pom.xml
├── LICENSE                                       (Apache-2.0)
├── README.md
├── .gitignore
├── .mvn/
│   └── jvm.config                                (optional, for IT memory tuning)
├── src/
│   ├── main/
│   │   ├── java/com/wlami/supplychain/
│   │   │   ├── mojo/
│   │   │   │   ├── AbstractSupplyChainMojo.java  (shared config + execution)
│   │   │   │   ├── CheckMojo.java                (@Mojo "check", validate phase)
│   │   │   │   ├── ReportMojo.java               (@Mojo "report", non-failing)
│   │   │   │   ├── DumpBaselineMojo.java         (@Mojo "dump-baseline")
│   │   │   │   └── RefreshCacheMojo.java         (@Mojo "refresh-cache")
│   │   │   ├── check/
│   │   │   │   ├── Check.java                    (interface)
│   │   │   │   ├── CheckContext.java             (per-run data: deps, session, config)
│   │   │   │   ├── CheckRegistry.java            (built-in checks)
│   │   │   │   ├── Severity.java                 (enum: ERROR | WARNING | NOTE)
│   │   │   │   ├── MinReleaseAgeCheck.java
│   │   │   │   ├── RequireExactVersionsCheck.java
│   │   │   │   ├── RepositoryAllowlistCheck.java
│   │   │   │   ├── BaselineCheck.java
│   │   │   │   ├── ChecksumPolicyCheck.java
│   │   │   │   ├── adapter/
│   │   │   │   │   ├── RequireReleaseDepsCheck.java
│   │   │   │   │   ├── BannedDependenciesCheck.java
│   │   │   │   │   └── DependencyConvergenceCheck.java
│   │   │   │   └── pgp/
│   │   │   │       ├── PgpSignatureCheck.java
│   │   │   │       ├── PgpVerifier.java          (BouncyCastle wrapper)
│   │   │   │       ├── KeyserverClient.java
│   │   │   │       ├── KeyCache.java
│   │   │   │       └── PgpKeysMap.java
│   │   │   ├── source/
│   │   │   │   ├── CentralSearchClient.java
│   │   │   │   ├── LastModifiedFallback.java
│   │   │   │   ├── ReleaseDateCache.java
│   │   │   │   └── HttpClientFactory.java
│   │   │   ├── config/
│   │   │   │   ├── PluginConfig.java
│   │   │   │   ├── ChecksConfig.java
│   │   │   │   ├── Override.java
│   │   │   │   └── GavPattern.java
│   │   │   └── report/
│   │   │       ├── Reporter.java                 (interface)
│   │   │       ├── Findings.java
│   │   │       ├── Finding.java
│   │   │       ├── ConsoleReporter.java
│   │   │       ├── SarifReporter.java
│   │   │       └── sarif/
│   │   │           ├── SarifModel.java           (POJO tree)
│   │   │           └── InputLocationResolver.java
│   │   └── resources/
│   │       └── (nothing for v0.1.0)
│   ├── test/
│   │   └── java/com/wlami/supplychain/
│   │       ├── check/
│   │       │   ├── MinReleaseAgeCheckTest.java
│   │       │   ├── RequireExactVersionsCheckTest.java
│   │       │   ├── RepositoryAllowlistCheckTest.java
│   │       │   ├── BaselineCheckTest.java
│   │       │   ├── ChecksumPolicyCheckTest.java
│   │       │   ├── adapter/
│   │       │   │   ├── RequireReleaseDepsCheckTest.java
│   │       │   │   ├── BannedDependenciesCheckTest.java
│   │       │   │   └── DependencyConvergenceCheckTest.java
│   │       │   └── pgp/
│   │       │       ├── PgpVerifierTest.java
│   │       │       ├── KeyCacheTest.java
│   │       │       └── PgpKeysMapTest.java
│   │       ├── source/
│   │       │   ├── CentralSearchClientTest.java   (WireMock)
│   │       │   ├── LastModifiedFallbackTest.java  (WireMock)
│   │       │   └── ReleaseDateCacheTest.java
│   │       ├── config/
│   │       │   └── GavPatternTest.java
│   │       └── report/
│   │           ├── ConsoleReporterTest.java
│   │           └── SarifReporterTest.java         (schema validation)
│   └── it/
│       ├── settings.xml
│       ├── pass-default-config/
│       ├── fail-min-age/
│       ├── fail-banned/
│       ├── fail-unsigned/
│       ├── fail-version-range/
│       ├── fail-repo-allowlist/
│       ├── pass-baseline/
│       └── fail-baseline-new-artifact/
└── docs/
    └── (existing spec + plan)
```

---

## Phase 0: Project bootstrap

### Task 1: Add `.gitignore` and `LICENSE`

**Files:**
- Create: `.gitignore`
- Create: `LICENSE`

- [ ] **Step 1: Write `.gitignore`**

```gitignore
target/
build/
out/
*.class
*.jar
*.war
*.iml
.idea/
.vscode/
.DS_Store
dependency-reduced-pom.xml
.mvn/wrapper/maven-wrapper.jar
.flattened-pom.xml
```

- [ ] **Step 2: Write Apache-2.0 license**

Use the canonical Apache 2.0 text. Fill the copyright line:

```
Copyright 2026 Wladi Mitzel
```

(Get the full text from https://www.apache.org/licenses/LICENSE-2.0.txt and place it in `LICENSE`.)

- [ ] **Step 3: Commit**

```bash
git add .gitignore LICENSE
git commit -m "chore: add Apache-2.0 license and .gitignore"
```

---

### Task 2: Bootstrap `pom.xml` and basic `CheckMojo` stub

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/wlami/supplychain/mojo/CheckMojo.java`

- [ ] **Step 1: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.wlami</groupId>
  <artifactId>supply-chain-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <packaging>maven-plugin</packaging>

  <name>supply-chain-maven-plugin</name>
  <description>Supply-chain hardening checks for Maven builds (min release age, PGP, baseline, etc.).</description>
  <url>https://github.com/wlami/supply-chain-maven-plugin</url>

  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>

  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <maven.version>3.8.8</maven.version>
    <enforcer.version>3.5.0</enforcer.version>
    <bouncycastle.version>1.78.1</bouncycastle.version>
    <jackson.version>2.17.2</jackson.version>

    <junit.version>5.10.3</junit.version>
    <assertj.version>3.26.3</assertj.version>
    <wiremock.version>3.9.1</wiremock.version>
    <json-schema-validator.version>1.5.1</json-schema-validator.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.apache.maven</groupId>
      <artifactId>maven-plugin-api</artifactId>
      <version>${maven.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.apache.maven</groupId>
      <artifactId>maven-core</artifactId>
      <version>${maven.version}</version>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>org.apache.maven.plugin-tools</groupId>
      <artifactId>maven-plugin-annotations</artifactId>
      <version>3.13.1</version>
      <scope>provided</scope>
    </dependency>

    <dependency>
      <groupId>org.apache.maven.enforcer</groupId>
      <artifactId>enforcer-rules</artifactId>
      <version>${enforcer.version}</version>
    </dependency>

    <dependency>
      <groupId>org.bouncycastle</groupId>
      <artifactId>bcpg-jdk18on</artifactId>
      <version>${bouncycastle.version}</version>
    </dependency>
    <dependency>
      <groupId>org.bouncycastle</groupId>
      <artifactId>bcprov-jdk18on</artifactId>
      <version>${bouncycastle.version}</version>
    </dependency>

    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>${junit.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <version>${assertj.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.wiremock</groupId>
      <artifactId>wiremock-standalone</artifactId>
      <version>${wiremock.version}</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>com.networknt</groupId>
      <artifactId>json-schema-validator</artifactId>
      <version>${json-schema-validator.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-plugin-plugin</artifactId>
        <version>3.13.1</version>
        <configuration>
          <goalPrefix>supply-chain</goalPrefix>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.3.1</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-invoker-plugin</artifactId>
        <version>3.8.1</version>
        <configuration>
          <projectsDirectory>src/it</projectsDirectory>
          <cloneProjectsTo>${project.build.directory}/it</cloneProjectsTo>
          <settingsFile>src/it/settings.xml</settingsFile>
          <postBuildHookScript>verify</postBuildHookScript>
        </configuration>
        <executions>
          <execution>
            <id>integration-test</id>
            <goals>
              <goal>install</goal>
              <goal>integration-test</goal>
              <goal>verify</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Write `CheckMojo` stub**

`src/main/java/com/wlami/supplychain/mojo/CheckMojo.java`:

```java
package com.wlami.supplychain.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = "check",
    defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public class CheckMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("supply-chain check (stub - no checks wired yet)");
    }
}
```

- [ ] **Step 3: Build to verify wiring**

Run: `mvn -q clean install -DskipTests`
Expected: BUILD SUCCESS. Plugin installed to local repo.

- [ ] **Step 4: Smoke-test the plugin against itself**

Run: `mvn com.wlami:supply-chain-maven-plugin:0.1.0-SNAPSHOT:check`
Expected: log line `supply-chain check (stub - no checks wired yet)`.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/wlami/supplychain/mojo/CheckMojo.java
git commit -m "feat: bootstrap maven plugin with check mojo stub"
```

---

## Phase 1: Core types and findings

### Task 3: `Severity` enum and `Finding` record

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/Severity.java`
- Create: `src/main/java/com/wlami/supplychain/report/Finding.java`
- Test: `src/test/java/com/wlami/supplychain/report/FindingTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FindingTest {
    @Test
    void fingerprintIsStableForSameGavAndCheck() {
        Finding a = Finding.builder()
            .checkId("minReleaseAge")
            .severity(Severity.ERROR)
            .gav("com.example:foo:1.0.0")
            .message("too young")
            .build();
        Finding b = Finding.builder()
            .checkId("minReleaseAge")
            .severity(Severity.ERROR)
            .gav("com.example:foo:1.0.0")
            .message("different message text")
            .build();
        assertThat(a.fingerprint()).isEqualTo(b.fingerprint());
    }

    @Test
    void fingerprintDiffersByCheckId() {
        Finding a = Finding.builder().checkId("a").gav("g:a:1").severity(Severity.ERROR).message("m").build();
        Finding b = Finding.builder().checkId("b").gav("g:a:1").severity(Severity.ERROR).message("m").build();
        assertThat(a.fingerprint()).isNotEqualTo(b.fingerprint());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=FindingTest`
Expected: compile failure (Severity, Finding not defined).

- [ ] **Step 3: Implement `Severity`**

```java
package com.wlami.supplychain.check;

public enum Severity {
    ERROR, WARNING, NOTE
}
```

- [ ] **Step 4: Implement `Finding`**

```java
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
```

- [ ] **Step 5: Run tests to verify pass**

Run: `mvn -q test -Dtest=FindingTest`
Expected: 2 tests, 2 passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/Severity.java \
        src/main/java/com/wlami/supplychain/report/Finding.java \
        src/test/java/com/wlami/supplychain/report/FindingTest.java
git commit -m "feat(core): add Severity enum and Finding value type"
```

---

### Task 4: `Findings` aggregate

**Files:**
- Create: `src/main/java/com/wlami/supplychain/report/Findings.java`
- Test: `src/test/java/com/wlami/supplychain/report/FindingsTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FindingsTest {
    @Test
    void emptyHasNoErrors() {
        Findings f = new Findings();
        assertThat(f.hasErrors()).isFalse();
        assertThat(f.all()).isEmpty();
    }

    @Test
    void addPreservesOrderAndDetectsErrors() {
        Findings f = new Findings();
        f.add(Finding.builder().checkId("c1").severity(Severity.WARNING).gav("g:a:1").message("m1").build());
        f.add(Finding.builder().checkId("c2").severity(Severity.ERROR).gav("g:b:1").message("m2").build());
        assertThat(f.hasErrors()).isTrue();
        assertThat(f.all()).hasSize(2);
        assertThat(f.errors()).hasSize(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=FindingsTest`
Expected: compile failure.

- [ ] **Step 3: Implement `Findings`**

```java
package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class Findings {
    private final List<Finding> items = new ArrayList<>();

    public void add(Finding f) { items.add(f); }
    public void addAll(Findings other) { items.addAll(other.items); }
    public List<Finding> all() { return Collections.unmodifiableList(items); }
    public List<Finding> errors() {
        return items.stream().filter(f -> f.severity() == Severity.ERROR).collect(Collectors.toList());
    }
    public boolean hasErrors() { return items.stream().anyMatch(f -> f.severity() == Severity.ERROR); }
    public int size() { return items.size(); }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=FindingsTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/report/Findings.java \
        src/test/java/com/wlami/supplychain/report/FindingsTest.java
git commit -m "feat(core): add Findings aggregate"
```

---

### Task 5: `Reporter` interface and `ConsoleReporter`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/report/Reporter.java`
- Create: `src/main/java/com/wlami/supplychain/report/ConsoleReporter.java`
- Test: `src/test/java/com/wlami/supplychain/report/ConsoleReporterTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import static org.assertj.core.api.Assertions.assertThat;

class ConsoleReporterTest {
    @Test
    void writesGroupedSummaryWithSeverityPrefix() {
        Findings f = new Findings();
        f.add(Finding.builder().checkId("minReleaseAge")
            .severity(Severity.ERROR).gav("com.example:foo:1.0.0")
            .message("published 1 day ago, threshold P3D").build());
        f.add(Finding.builder().checkId("pgpSignature")
            .severity(Severity.ERROR).gav("com.example:bar:2.0.0")
            .message("missing .asc").build());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConsoleReporter(new PrintStream(out)).write(f);

        String text = out.toString();
        assertThat(text).contains("[minReleaseAge]");
        assertThat(text).contains("com.example:foo:1.0.0");
        assertThat(text).contains("[pgpSignature]");
        assertThat(text).contains("supply-chain check failed");
    }

    @Test
    void emptyFindingsWriteSuccessLine() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConsoleReporter(new PrintStream(out)).write(new Findings());
        assertThat(out.toString()).contains("supply-chain check passed");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q test -Dtest=ConsoleReporterTest`
Expected: compile failure.

- [ ] **Step 3: Implement `Reporter` interface**

```java
package com.wlami.supplychain.report;

public interface Reporter {
    void write(Findings findings);
}
```

- [ ] **Step 4: Implement `ConsoleReporter`**

```java
package com.wlami.supplychain.report;

import com.wlami.supplychain.check.Severity;
import java.io.PrintStream;

public final class ConsoleReporter implements Reporter {

    private final PrintStream out;

    public ConsoleReporter(PrintStream out) { this.out = out; }

    @Override
    public void write(Findings findings) {
        if (findings.all().isEmpty()) {
            out.println("[INFO] supply-chain check passed");
            return;
        }
        if (findings.hasErrors()) {
            out.println("[ERROR] supply-chain check failed:");
        } else {
            out.println("[WARNING] supply-chain check produced warnings:");
        }
        for (Finding f : findings.all()) {
            String prefix = f.severity() == Severity.ERROR ? "  [ERROR]"
                : f.severity() == Severity.WARNING ? "  [WARN] " : "  [NOTE] ";
            out.printf("%s [%s] %s %s%n", prefix, f.checkId(), f.gav(), f.message());
        }
        out.println("Run `mvn supply-chain:report` for full details without failing.");
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=ConsoleReporterTest`
Expected: 2 passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/wlami/supplychain/report/Reporter.java \
        src/main/java/com/wlami/supplychain/report/ConsoleReporter.java \
        src/test/java/com/wlami/supplychain/report/ConsoleReporterTest.java
git commit -m "feat(report): add Reporter interface and ConsoleReporter"
```

---

## Phase 2: GAV patterns and config model

### Task 6: `GavPattern` matcher

**Files:**
- Create: `src/main/java/com/wlami/supplychain/config/GavPattern.java`
- Test: `src/test/java/com/wlami/supplychain/config/GavPatternTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GavPatternTest {

    @Test
    void exactGavMatches() {
        GavPattern p = GavPattern.parse("com.example:foo:1.0.0");
        assertThat(p.matches("com.example", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "foo", "1.0.1")).isFalse();
    }

    @Test
    void wildcardArtifactMatches() {
        GavPattern p = GavPattern.parse("com.example:*");
        assertThat(p.matches("com.example", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "bar", "9.9.9")).isTrue();
        assertThat(p.matches("com.other", "foo", "1.0.0")).isFalse();
    }

    @Test
    void wildcardGroupSuffixMatches() {
        GavPattern p = GavPattern.parse("com.example.*:*");
        assertThat(p.matches("com.example.internal", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "foo", "1.0.0")).isFalse();
    }

    @Test
    void omittedVersionAllowsAny() {
        GavPattern p = GavPattern.parse("com.example:foo");
        assertThat(p.matches("com.example", "foo", "1.0.0")).isTrue();
        assertThat(p.matches("com.example", "foo", "2.0.0")).isTrue();
    }

    @Test
    void invalidPatternThrows() {
        assertThat(java.util.Optional.ofNullable(
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GavPattern.parse("no-colon")))).isPresent();
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `mvn -q test -Dtest=GavPatternTest`
Expected: compile failure.

- [ ] **Step 3: Implement `GavPattern`**

```java
package com.wlami.supplychain.config;

import java.util.regex.Pattern;

public final class GavPattern {

    private final Pattern group;
    private final Pattern artifact;
    private final Pattern version; // null = match any

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
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=GavPatternTest`
Expected: 5 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/config/GavPattern.java \
        src/test/java/com/wlami/supplychain/config/GavPatternTest.java
git commit -m "feat(config): add GavPattern glob matcher"
```

---

### Task 7: `PluginConfig`, `ChecksConfig`, `Override` value types

**Files:**
- Create: `src/main/java/com/wlami/supplychain/config/PluginConfig.java`
- Create: `src/main/java/com/wlami/supplychain/config/ChecksConfig.java`
- Create: `src/main/java/com/wlami/supplychain/config/Override.java`

- [ ] **Step 1: Implement `Override`**

```java
package com.wlami.supplychain.config;

import java.time.Duration;

public final class Override {
    /** Plain string for Maven XML binding; parsed to GavPattern at use time. */
    public String pattern;
    /** ISO-8601 duration, e.g. "P0D" to disable for matched artifacts. */
    public String minReleaseAge;

    public GavPattern parsePattern() { return GavPattern.parse(pattern); }
    public Duration parseMinReleaseAge() { return minReleaseAge == null ? null : Duration.parse(minReleaseAge); }
}
```

- [ ] **Step 2: Implement `ChecksConfig`**

```java
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
```

- [ ] **Step 3: Implement `PluginConfig`**

```java
package com.wlami.supplychain.config;

import java.util.ArrayList;
import java.util.List;

public final class PluginConfig {
    public String minReleaseAge = "P3D";
    /** GAV patterns whose min-release-age check is skipped entirely. */
    public List<String> minReleaseAgeExclusions = new ArrayList<>();
    public List<String> repositoryAllowlist = new ArrayList<>(List.of("https://repo.maven.apache.org/maven2"));
    public List<String> bannedDependencies = new ArrayList<>();
    public String pgpKeysMap; // optional path
    public List<String> excludes = new ArrayList<>();
    public List<Override> overrides = new ArrayList<>();
    public ChecksConfig checks = new ChecksConfig();
    public String sarifOutput; // null defaulted by mojo to ${project.build.directory}/supply-chain.sarif
    /** "FAIL" | "WARN" */
    public String onViolation = "FAIL";
    /** "FAIL" | "WARN" | "SKIP" */
    public String onNetworkError = "FAIL";
}
```

- [ ] **Step 4: Build to confirm compilation**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/config/PluginConfig.java \
        src/main/java/com/wlami/supplychain/config/ChecksConfig.java \
        src/main/java/com/wlami/supplychain/config/Override.java
git commit -m "feat(config): add PluginConfig, ChecksConfig, Override value types"
```

---

## Phase 3: Check abstraction

### Task 8: `Check` interface and `CheckContext`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/Check.java`
- Create: `src/main/java/com/wlami/supplychain/check/CheckContext.java`

- [ ] **Step 1: Implement `CheckContext`**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import java.util.Set;
import org.apache.maven.artifact.Artifact;

public final class CheckContext {

    private final MavenProject project;
    private final MavenSession session;
    private final PluginConfig config;
    private final Set<Artifact> dependencies;

    public CheckContext(MavenProject project, MavenSession session, PluginConfig config, Set<Artifact> dependencies) {
        this.project = project;
        this.session = session;
        this.config = config;
        this.dependencies = dependencies;
    }

    public MavenProject project() { return project; }
    public MavenSession session() { return session; }
    public PluginConfig config() { return config; }
    public Set<Artifact> dependencies() { return dependencies; }
}
```

- [ ] **Step 2: Implement `Check`**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Findings;

public interface Check {
    /** Stable ID used by config flags, reports, and SARIF rule IDs. */
    String id();
    /** Human-readable name for documentation. */
    String name();
    /** Returns true if the check is currently enabled per config. */
    boolean isEnabled(CheckContext ctx);
    /** Run the check; append any findings to the returned Findings. */
    Findings run(CheckContext ctx) throws Exception;
}
```

- [ ] **Step 3: Build**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/Check.java \
        src/main/java/com/wlami/supplychain/check/CheckContext.java
git commit -m "feat(check): add Check interface and CheckContext"
```

---

## Phase 4: Central Search client + release-date cache

### Task 9: `HttpClientFactory`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/source/HttpClientFactory.java`

- [ ] **Step 1: Implement**

```java
package com.wlami.supplychain.source;

import java.net.http.HttpClient;
import java.time.Duration;

public final class HttpClientFactory {
    private HttpClientFactory() {}
    public static HttpClient create() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }
}
```

- [ ] **Step 2: Build**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wlami/supplychain/source/HttpClientFactory.java
git commit -m "feat(source): add HttpClientFactory"
```

---

### Task 10: `CentralSearchClient` with WireMock test

**Files:**
- Create: `src/main/java/com/wlami/supplychain/source/CentralSearchClient.java`
- Test: `src/test/java/com/wlami/supplychain/source/CentralSearchClientTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.source;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Optional;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class CentralSearchClientTest {

    private WireMockServer wm;

    @BeforeEach void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        WireMock.configureFor("localhost", wm.port());
    }

    @AfterEach void stop() { wm.stop(); }

    @Test
    void returnsTimestampWhenCentralResponds() {
        wm.stubFor(get(urlPathEqualTo("/solrsearch/select"))
            .withQueryParam("q", equalTo("g:com.example AND a:foo AND v:1.0.0"))
            .willReturn(okJson("{\"response\":{\"docs\":[{\"timestamp\":1715000000000}]}}")));

        CentralSearchClient client = new CentralSearchClient("http://localhost:" + wm.port());
        Optional<Instant> ts = client.fetchReleaseDate("com.example", "foo", "1.0.0");
        assertThat(ts).isPresent();
        assertThat(ts.get().toEpochMilli()).isEqualTo(1715000000000L);
    }

    @Test
    void returnsEmptyWhenNoDocs() {
        wm.stubFor(get(urlPathEqualTo("/solrsearch/select"))
            .willReturn(okJson("{\"response\":{\"docs\":[]}}")));
        CentralSearchClient client = new CentralSearchClient("http://localhost:" + wm.port());
        assertThat(client.fetchReleaseDate("g", "a", "v")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `mvn -q test -Dtest=CentralSearchClientTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class CentralSearchClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String baseUrl;
    private final HttpClient http;

    public CentralSearchClient(String baseUrl) {
        this(baseUrl, HttpClientFactory.create());
    }

    public CentralSearchClient(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
    }

    public Optional<Instant> fetchReleaseDate(String groupId, String artifactId, String version) {
        try {
            String q = URLEncoder.encode("g:" + groupId + " AND a:" + artifactId + " AND v:" + version,
                StandardCharsets.UTF_8);
            URI uri = URI.create(baseUrl + "/solrsearch/select?q=" + q + "&core=gav&rows=1&wt=json");
            HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("Central search HTTP " + resp.statusCode());
            }
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode docs = root.path("response").path("docs");
            if (!docs.isArray() || docs.size() == 0) return Optional.empty();
            long ts = docs.get(0).path("timestamp").asLong(-1L);
            if (ts < 0) return Optional.empty();
            return Optional.of(Instant.ofEpochMilli(ts));
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Central search failed for " + groupId + ":" + artifactId + ":" + version, e);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=CentralSearchClientTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/source/CentralSearchClient.java \
        src/test/java/com/wlami/supplychain/source/CentralSearchClientTest.java
git commit -m "feat(source): add CentralSearchClient with Solr query"
```

---

### Task 11: `ReleaseDateCache`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/source/ReleaseDateCache.java`
- Test: `src/test/java/com/wlami/supplychain/source/ReleaseDateCacheTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class ReleaseDateCacheTest {

    @Test
    void getReturnsValueWhenStored(@TempDir Path tmp) {
        ReleaseDateCache cache = new ReleaseDateCache(tmp.resolve("c.json"));
        cache.put("g:a:1", Instant.ofEpochMilli(123_456_789L));
        cache.flush();

        ReleaseDateCache reloaded = new ReleaseDateCache(tmp.resolve("c.json"));
        Optional<Instant> got = reloaded.get("g:a:1");
        assertThat(got).contains(Instant.ofEpochMilli(123_456_789L));
    }

    @Test
    void missingKeyReturnsEmpty(@TempDir Path tmp) {
        ReleaseDateCache cache = new ReleaseDateCache(tmp.resolve("c.json"));
        assertThat(cache.get("g:a:1")).isEmpty();
    }

    @Test
    void invalidateRemovesKey(@TempDir Path tmp) {
        ReleaseDateCache cache = new ReleaseDateCache(tmp.resolve("c.json"));
        cache.put("g:a:1", Instant.now());
        cache.invalidate("g:a:1");
        assertThat(cache.get("g:a:1")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `mvn -q test -Dtest=ReleaseDateCacheTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.source;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ReleaseDateCache {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path file;
    private final Map<String, Long> data;

    public ReleaseDateCache(Path file) {
        this.file = file;
        this.data = load(file);
    }

    private static Map<String, Long> load(Path file) {
        try {
            if (!Files.exists(file)) return new ConcurrentHashMap<>();
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return new ConcurrentHashMap<>();
            Map<String, Long> read = MAPPER.readValue(bytes, new TypeReference<Map<String, Long>>() {});
            return new ConcurrentHashMap<>(read);
        } catch (IOException e) {
            return new ConcurrentHashMap<>();
        }
    }

    public Optional<Instant> get(String gav) {
        Long v = data.get(gav);
        return v == null ? Optional.empty() : Optional.of(Instant.ofEpochMilli(v));
    }

    public void put(String gav, Instant ts) { data.put(gav, ts.toEpochMilli()); }
    public void invalidate(String gav) { data.remove(gav); }
    public void clear() { data.clear(); }

    public void flush() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.write(file, MAPPER.writeValueAsBytes(new HashMap<>(data)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write cache: " + file, e);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=ReleaseDateCacheTest`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/source/ReleaseDateCache.java \
        src/test/java/com/wlami/supplychain/source/ReleaseDateCacheTest.java
git commit -m "feat(source): add ReleaseDateCache backed by JSON file"
```

---

## Phase 5: First check - `MinReleaseAgeCheck`

### Task 12: `MinReleaseAgeCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/MinReleaseAgeCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/MinReleaseAgeCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.source.CentralSearchClient;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MinReleaseAgeCheckTest {

    private static Artifact artifact(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void flagsArtifactYoungerThanThreshold() throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        Instant oneDayOld = now.minusSeconds(86_400);
        CentralSearchClient client = mock(CentralSearchClient.class);
        when(client.fetchReleaseDate("com.example", "foo", "1.0.0")).thenReturn(Optional.of(oneDayOld));

        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAge = "P3D";
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));

        Findings findings = new MinReleaseAgeCheck(client, Clock.fixed(now, ZoneId.of("UTC"))).run(ctx);

        assertThat(findings.all()).hasSize(1);
        assertThat(findings.all().get(0).gav()).isEqualTo("com.example:foo:1.0.0");
        assertThat(findings.all().get(0).message()).contains("P3D");
    }

    @Test
    void passesArtifactOlderThanThreshold() throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        Instant tenDaysOld = now.minusSeconds(10 * 86_400);
        CentralSearchClient client = mock(CentralSearchClient.class);
        when(client.fetchReleaseDate("com.example", "foo", "1.0.0")).thenReturn(Optional.of(tenDaysOld));

        PluginConfig cfg = new PluginConfig();
        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "foo", "1.0.0")));

        Findings findings = new MinReleaseAgeCheck(client, Clock.fixed(now, ZoneId.of("UTC"))).run(ctx);
        assertThat(findings.all()).isEmpty();
    }

    @Test
    void overrideRelaxesAgeForMatchingArtifact() throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        Instant minutesOld = now.minusSeconds(60);
        CentralSearchClient client = mock(CentralSearchClient.class);
        when(client.fetchReleaseDate("com.example", "internal", "1.0.0")).thenReturn(Optional.of(minutesOld));

        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAge = "P3D";
        com.wlami.supplychain.config.Override ov = new com.wlami.supplychain.config.Override();
        ov.pattern = "com.example:internal*"; ov.minReleaseAge = "P0D";
        cfg.overrides.add(ov);

        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.example", "internal", "1.0.0")));
        Findings findings = new MinReleaseAgeCheck(client, Clock.fixed(now, ZoneId.of("UTC"))).run(ctx);
        assertThat(findings.all()).isEmpty();
    }

    @Test
    void exclusionListSkipsCheckEntirely() throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        CentralSearchClient client = mock(CentralSearchClient.class);
        // The client must never be called for excluded GAVs.
        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAge = "P3D";
        cfg.minReleaseAgeExclusions = java.util.List.of("com.wlami:my-artifact");

        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(artifact("com.wlami", "my-artifact", "1.0.0")));
        Findings findings = new MinReleaseAgeCheck(client, Clock.fixed(now, ZoneId.of("UTC"))).run(ctx);
        assertThat(findings.all()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void exclusionWildcardMatchesGroup() throws Exception {
        Instant now = Instant.parse("2026-05-14T12:00:00Z");
        CentralSearchClient client = mock(CentralSearchClient.class);
        PluginConfig cfg = new PluginConfig();
        cfg.minReleaseAgeExclusions = java.util.List.of("com.wlami:*");

        CheckContext ctx = new CheckContext(null, null, cfg, Set.of(
            artifact("com.wlami", "any-artifact", "1.0.0"),
            artifact("com.wlami", "other-artifact", "9.9.9")));
        Findings findings = new MinReleaseAgeCheck(client, Clock.fixed(now, ZoneId.of("UTC"))).run(ctx);
        assertThat(findings.all()).isEmpty();
        verifyNoInteractions(client);
    }
}
```

Add Mockito as a test dep in `pom.xml`:

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <version>5.12.0</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Run test, verify it fails**

Run: `mvn -q test -Dtest=MinReleaseAgeCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement `MinReleaseAgeCheck`**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.GavPattern;
import com.wlami.supplychain.config.Override;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.source.CentralSearchClient;
import org.apache.maven.artifact.Artifact;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class MinReleaseAgeCheck implements Check {

    private final CentralSearchClient client;
    private final Clock clock;

    public MinReleaseAgeCheck(CentralSearchClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override public String id() { return "minReleaseAge"; }
    @Override public String name() { return "Minimum release age"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.minReleaseAge; }

    @Override
    public Findings run(CheckContext ctx) throws Exception {
        Findings findings = new Findings();
        Duration defaultMin = Duration.parse(ctx.config().minReleaseAge);
        Instant now = clock.instant();

        java.util.List<GavPattern> exclusions = new java.util.ArrayList<>();
        for (String raw : ctx.config().minReleaseAgeExclusions) exclusions.add(GavPattern.parse(raw));

        for (Artifact a : ctx.dependencies()) {
            if (matchesAny(exclusions, a)) continue;
            Duration min = effectiveMin(ctx, a, defaultMin);
            if (min.isZero() || min.isNegative()) continue;
            Optional<Instant> published = client.fetchReleaseDate(a.getGroupId(), a.getArtifactId(), a.getVersion());
            if (published.isEmpty()) continue;
            Duration age = Duration.between(published.get(), now);
            if (age.compareTo(min) < 0) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion())
                    .message(String.format("published %s (%s ago, threshold %s)",
                        published.get(), human(age), min))
                    .property("publishedAt", published.get().toString())
                    .property("threshold", min.toString())
                    .build());
            }
        }
        return findings;
    }

    private static Duration effectiveMin(CheckContext ctx, Artifact a, Duration def) {
        for (Override ov : ctx.config().overrides) {
            if (ov.minReleaseAge == null) continue;
            GavPattern p = ov.parsePattern();
            if (p.matches(a.getGroupId(), a.getArtifactId(), a.getVersion())) {
                return ov.parseMinReleaseAge();
            }
        }
        return def;
    }

    private static boolean matchesAny(java.util.List<GavPattern> patterns, Artifact a) {
        for (GavPattern p : patterns) {
            if (p.matches(a.getGroupId(), a.getArtifactId(), a.getVersion())) return true;
        }
        return false;
    }

    private static String human(Duration d) {
        long days = d.toDays();
        if (days > 0) return days + "d";
        long hours = d.toHours();
        if (hours > 0) return hours + "h";
        return d.toMinutes() + "m";
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=MinReleaseAgeCheckTest`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/MinReleaseAgeCheck.java \
        src/test/java/com/wlami/supplychain/check/MinReleaseAgeCheckTest.java \
        pom.xml
git commit -m "feat(check): add MinReleaseAgeCheck with overrides"
```

---

## Phase 6: Remaining own checks

### Task 13: `RequireExactVersionsCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/RequireExactVersionsCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/RequireExactVersionsCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RequireExactVersionsCheckTest {

    private static Dependency dep(String g, String a, String v) {
        Dependency d = new Dependency();
        d.setGroupId(g); d.setArtifactId(a); d.setVersion(v);
        return d;
    }

    @Test
    void flagsVersionRange() throws Exception {
        MavenProject p = new MavenProject();
        p.setDependencies(List.of(dep("g", "a", "[1.0,2.0)")));
        Findings f = new RequireExactVersionsCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).message()).contains("[1.0,2.0)");
    }

    @Test
    void flagsLatestAndRelease() throws Exception {
        MavenProject p = new MavenProject();
        p.setDependencies(List.of(dep("g", "a", "LATEST"), dep("g", "b", "RELEASE")));
        Findings f = new RequireExactVersionsCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).hasSize(2);
    }

    @Test
    void passesExactVersion() throws Exception {
        MavenProject p = new MavenProject();
        p.setDependencies(List.of(dep("g", "a", "1.2.3")));
        Findings f = new RequireExactVersionsCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=RequireExactVersionsCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.model.Dependency;
import java.util.regex.Pattern;

public final class RequireExactVersionsCheck implements Check {

    private static final Pattern RANGE = Pattern.compile("^[\\[(].*[\\])]$");

    @Override public String id() { return "requireExactVersions"; }
    @Override public String name() { return "Require exact dependency versions"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.requireExactVersions; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        if (ctx.project() == null) return findings;
        for (Dependency d : ctx.project().getDependencies()) {
            String v = d.getVersion();
            if (v == null) continue;
            String reason = problemFor(v);
            if (reason != null) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(d.getGroupId() + ":" + d.getArtifactId() + ":" + v)
                    .message(reason)
                    .build());
            }
        }
        return findings;
    }

    private static String problemFor(String v) {
        if (RANGE.matcher(v).matches()) return "version range not allowed: " + v;
        if ("LATEST".equals(v)) return "LATEST keyword not allowed";
        if ("RELEASE".equals(v)) return "RELEASE keyword not allowed";
        return null;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=RequireExactVersionsCheckTest`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/RequireExactVersionsCheck.java \
        src/test/java/com/wlami/supplychain/check/RequireExactVersionsCheckTest.java
git commit -m "feat(check): add RequireExactVersionsCheck"
```

---

### Task 14: `RepositoryAllowlistCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/RepositoryAllowlistCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/RepositoryAllowlistCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RepositoryAllowlistCheckTest {

    private static MavenProject projectWithRepos(String... urls) {
        MavenProject p = new MavenProject();
        java.util.List<org.apache.maven.artifact.repository.ArtifactRepository> repos = new java.util.ArrayList<>();
        for (String u : urls) {
            MavenArtifactRepository r = new MavenArtifactRepository();
            r.setId("r-" + u.hashCode());
            r.setUrl(u);
            repos.add(r);
        }
        p.setRemoteArtifactRepositories(repos);
        return p;
    }

    @Test
    void passesWhenAllReposAllowlisted() throws Exception {
        MavenProject p = projectWithRepos("https://repo.maven.apache.org/maven2");
        Findings f = new RepositoryAllowlistCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).isEmpty();
    }

    @Test
    void failsWhenUnknownRepoPresent() throws Exception {
        MavenProject p = projectWithRepos(
            "https://repo.maven.apache.org/maven2",
            "https://evil.example/repo");
        Findings f = new RepositoryAllowlistCheck().run(
            new CheckContext(p, null, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).message()).contains("evil.example");
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=RepositoryAllowlistCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.repository.ArtifactRepository;
import java.util.HashSet;
import java.util.Set;

public final class RepositoryAllowlistCheck implements Check {

    @Override public String id() { return "repositoryAllowlist"; }
    @Override public String name() { return "Repository allowlist"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.repositoryAllowlist; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        if (ctx.project() == null) return findings;
        Set<String> allowed = new HashSet<>();
        for (String u : ctx.config().repositoryAllowlist) allowed.add(normalize(u));

        for (ArtifactRepository r : ctx.project().getRemoteArtifactRepositories()) {
            String norm = normalize(r.getUrl());
            if (!allowed.contains(norm)) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(r.getId())
                    .message("repository not on allowlist: " + r.getUrl())
                    .property("url", r.getUrl())
                    .build());
            }
        }
        return findings;
    }

    private static String normalize(String url) {
        if (url == null) return "";
        String s = url.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=RepositoryAllowlistCheckTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/RepositoryAllowlistCheck.java \
        src/test/java/com/wlami/supplychain/check/RepositoryAllowlistCheckTest.java
git commit -m "feat(check): add RepositoryAllowlistCheck"
```

---

### Task 15: `ChecksumPolicyCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/ChecksumPolicyCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/ChecksumPolicyCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.assertThat;

class ChecksumPolicyCheckTest {

    @Test
    void warnsWhenChecksumPolicyNotStrict() throws Exception {
        MavenExecutionRequest req = Mockito.mock(MavenExecutionRequest.class);
        Mockito.when(req.getGlobalChecksumPolicy()).thenReturn(null);
        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.getRequest()).thenReturn(req);

        PluginConfig cfg = new PluginConfig();
        cfg.checks.checksumStrict = "warn";
        Findings f = new ChecksumPolicyCheck().run(
            new CheckContext(null, session, cfg, java.util.Set.of()));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).severity()).isEqualTo(Severity.WARNING);
    }

    @Test
    void passesWhenChecksumPolicyIsFail() throws Exception {
        MavenExecutionRequest req = Mockito.mock(MavenExecutionRequest.class);
        Mockito.when(req.getGlobalChecksumPolicy()).thenReturn("fail");
        MavenSession session = Mockito.mock(MavenSession.class);
        Mockito.when(session.getRequest()).thenReturn(req);

        Findings f = new ChecksumPolicyCheck().run(
            new CheckContext(null, session, new PluginConfig(), java.util.Set.of()));
        assertThat(f.all()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=ChecksumPolicyCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.execution.MavenSession;

public final class ChecksumPolicyCheck implements Check {

    @Override public String id() { return "checksumStrict"; }
    @Override public String name() { return "Checksum strict mode"; }
    @Override public boolean isEnabled(CheckContext ctx) {
        return !"off".equalsIgnoreCase(ctx.config().checks.checksumStrict);
    }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        MavenSession session = ctx.session();
        if (session == null) return findings;
        String policy = session.getRequest() == null ? null : session.getRequest().getGlobalChecksumPolicy();
        if ("fail".equalsIgnoreCase(policy)) return findings;

        String mode = ctx.config().checks.checksumStrict;
        Severity sev = "fail".equalsIgnoreCase(mode) ? Severity.ERROR : Severity.WARNING;
        findings.add(Finding.builder()
            .checkId(id())
            .severity(sev)
            .gav("(maven settings)")
            .message("checksum policy not strict (current: " + policy + "). Use `-C` or settings.xml `<checksumPolicy>fail</checksumPolicy>`.")
            .build());
        return findings;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=ChecksumPolicyCheckTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/ChecksumPolicyCheck.java \
        src/test/java/com/wlami/supplychain/check/ChecksumPolicyCheckTest.java
git commit -m "feat(check): add ChecksumPolicyCheck"
```

---

### Task 16: `BaselineCheck` + `DumpBaselineMojo`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/BaselineCheck.java`
- Create: `src/main/java/com/wlami/supplychain/mojo/DumpBaselineMojo.java`
- Test: `src/test/java/com/wlami/supplychain/check/BaselineCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class BaselineCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void autoSkipsWhenFileMissing(@TempDir Path tmp) throws Exception {
        MavenProject p = new MavenProject();
        p.setFile(tmp.resolve("pom.xml").toFile());
        PluginConfig cfg = new PluginConfig();
        Findings f = new BaselineCheck().run(
            new CheckContext(p, null, cfg, Set.of(art("g", "a", "1"))));
        assertThat(f.all()).isEmpty();
    }

    @Test
    void flagsArtifactNotInBaseline(@TempDir Path tmp) throws Exception {
        Path baseline = tmp.resolve(".supply-chain-baseline.json");
        Files.writeString(baseline, "{\"artifacts\":[\"g:a:1\"]}");

        MavenProject p = new MavenProject();
        p.setFile(tmp.resolve("pom.xml").toFile());

        Findings f = new BaselineCheck().run(
            new CheckContext(p, null, new PluginConfig(),
                Set.of(art("g", "a", "1"), art("g", "b", "2"))));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).gav()).isEqualTo("g:b:2");
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=BaselineCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement `BaselineCheck`**

```java
package com.wlami.supplychain.check;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class BaselineCheck implements Check {

    public static final String DEFAULT_FILENAME = ".supply-chain-baseline.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override public String id() { return "baseline"; }
    @Override public String name() { return "Dependency baseline"; }
    @Override public boolean isEnabled(CheckContext ctx) {
        String mode = ctx.config().checks.baseline;
        return "true".equalsIgnoreCase(mode) || "auto".equalsIgnoreCase(mode);
    }

    @Override
    public Findings run(CheckContext ctx) throws IOException {
        Findings findings = new Findings();
        if (ctx.project() == null || ctx.project().getFile() == null) return findings;

        Path baseline = ctx.project().getFile().toPath().getParent().resolve(DEFAULT_FILENAME);
        boolean explicit = "true".equalsIgnoreCase(ctx.config().checks.baseline);
        if (!Files.exists(baseline)) {
            if (explicit) {
                findings.add(Finding.builder()
                    .checkId(id()).severity(Severity.ERROR)
                    .gav("(baseline)").message("baseline file missing: " + baseline)
                    .build());
            }
            return findings;
        }

        Set<String> approved = readBaseline(baseline);
        for (Artifact a : ctx.dependencies()) {
            String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
            if (!approved.contains(gav)) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(gav)
                    .message("artifact not in baseline (run `mvn supply-chain:dump-baseline` to approve)")
                    .build());
            }
        }
        return findings;
    }

    static Set<String> readBaseline(Path file) throws IOException {
        JsonNode root = MAPPER.readTree(Files.readAllBytes(file));
        Set<String> out = new HashSet<>();
        JsonNode arts = root.path("artifacts");
        if (arts.isArray()) for (JsonNode n : arts) out.add(n.asText());
        return out;
    }
}
```

- [ ] **Step 4: Implement `DumpBaselineMojo`**

```java
package com.wlami.supplychain.mojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wlami.supplychain.check.BaselineCheck;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;

@Mojo(name = "dump-baseline", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class DumpBaselineMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        TreeSet<String> gavs = new TreeSet<>();
        for (Artifact a : project.getArtifacts()) {
            gavs.add(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion());
        }
        Path out = project.getBasedir().toPath().resolve(BaselineCheck.DEFAULT_FILENAME);
        try {
            ObjectMapper m = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Files.writeString(out, m.writeValueAsString(Map.of("artifacts", gavs)));
            getLog().info("Wrote baseline to " + out + " (" + gavs.size() + " artifacts)");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write baseline", e);
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=BaselineCheckTest`
Expected: 2 passed.

- [ ] **Step 6: Build**

Run: `mvn -q install -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/BaselineCheck.java \
        src/main/java/com/wlami/supplychain/mojo/DumpBaselineMojo.java \
        src/test/java/com/wlami/supplychain/check/BaselineCheckTest.java
git commit -m "feat(check): add BaselineCheck and dump-baseline mojo"
```

---

## Phase 7: Enforcer-rule adapters

### Task 17: `RequireReleaseDepsCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/adapter/RequireReleaseDepsCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/adapter/RequireReleaseDepsCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class RequireReleaseDepsCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void flagsSnapshotDependency() throws Exception {
        Findings f = new RequireReleaseDepsCheck().run(
            new CheckContext(null, null, new PluginConfig(),
                Set.of(art("g", "a", "1.0-SNAPSHOT"))));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void passesReleaseDependency() throws Exception {
        Findings f = new RequireReleaseDepsCheck().run(
            new CheckContext(null, null, new PluginConfig(),
                Set.of(art("g", "a", "1.0.0"))));
        assertThat(f.all()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=RequireReleaseDepsCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;

public final class RequireReleaseDepsCheck implements Check {

    @Override public String id() { return "requireReleaseDeps"; }
    @Override public String name() { return "Require release dependencies"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.requireReleaseDeps; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        for (Artifact a : ctx.dependencies()) {
            if (a.isSnapshot()) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion())
                    .message("SNAPSHOT dependency not allowed")
                    .build());
            }
        }
        return findings;
    }
}
```

Note: this is a simpler reimplementation than wrapping `enforcer-rules`. `enforcer-rules`' `requireReleaseDeps` rule is tightly coupled to `EnforcerRuleHelper`; the same logic in ~10 LoC keeps the test simple and avoids the coupling.

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=RequireReleaseDepsCheckTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/adapter/RequireReleaseDepsCheck.java \
        src/test/java/com/wlami/supplychain/check/adapter/RequireReleaseDepsCheckTest.java
git commit -m "feat(check): add RequireReleaseDepsCheck (SNAPSHOT detector)"
```

---

### Task 18: `BannedDependenciesCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/adapter/BannedDependenciesCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/adapter/BannedDependenciesCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class BannedDependenciesCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void flagsBannedGav() throws Exception {
        PluginConfig cfg = new PluginConfig();
        cfg.bannedDependencies = List.of("log4j:log4j");
        Findings f = new BannedDependenciesCheck().run(
            new CheckContext(null, null, cfg, Set.of(art("log4j", "log4j", "1.2.17"))));
        assertThat(f.all()).hasSize(1);
    }

    @Test
    void passesAllowedGav() throws Exception {
        PluginConfig cfg = new PluginConfig();
        cfg.bannedDependencies = List.of("log4j:log4j");
        Findings f = new BannedDependenciesCheck().run(
            new CheckContext(null, null, cfg, Set.of(art("org.slf4j", "slf4j-api", "2.0.0"))));
        assertThat(f.all()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=BannedDependenciesCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.config.GavPattern;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import java.util.ArrayList;
import java.util.List;

public final class BannedDependenciesCheck implements Check {

    @Override public String id() { return "bannedDependencies"; }
    @Override public String name() { return "Banned dependencies"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.bannedDependencies; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        List<GavPattern> bans = new ArrayList<>();
        for (String b : ctx.config().bannedDependencies) bans.add(GavPattern.parse(b));
        if (bans.isEmpty()) return findings;

        for (Artifact a : ctx.dependencies()) {
            for (GavPattern p : bans) {
                if (p.matches(a.getGroupId(), a.getArtifactId(), a.getVersion())) {
                    findings.add(Finding.builder()
                        .checkId(id())
                        .severity(Severity.ERROR)
                        .gav(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion())
                        .message("banned by configuration")
                        .build());
                    break;
                }
            }
        }
        return findings;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=BannedDependenciesCheckTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/adapter/BannedDependenciesCheck.java \
        src/test/java/com/wlami/supplychain/check/adapter/BannedDependenciesCheckTest.java
git commit -m "feat(check): add BannedDependenciesCheck"
```

---

### Task 19: `DependencyConvergenceCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/adapter/DependencyConvergenceCheck.java`
- Test: `src/test/java/com/wlami/supplychain/check/adapter/DependencyConvergenceCheckTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class DependencyConvergenceCheckTest {

    private static Artifact art(String g, String a, String v) {
        return new DefaultArtifact(g, a, v, "compile", "jar", null, new DefaultArtifactHandler("jar"));
    }

    @Test
    void flagsDivergentVersions() throws Exception {
        Findings f = new DependencyConvergenceCheck().run(
            new CheckContext(null, null, new PluginConfig(),
                Set.of(art("g", "a", "1.0"), art("g", "a", "2.0"))));
        assertThat(f.all()).hasSize(1);
        assertThat(f.all().get(0).message()).contains("1.0").contains("2.0");
    }

    @Test
    void passesSingleVersion() throws Exception {
        Findings f = new DependencyConvergenceCheck().run(
            new CheckContext(null, null, new PluginConfig(),
                Set.of(art("g", "a", "1.0"), art("g", "b", "2.0"))));
        assertThat(f.all()).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=DependencyConvergenceCheckTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check.adapter;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.artifact.Artifact;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class DependencyConvergenceCheck implements Check {

    @Override public String id() { return "dependencyConvergence"; }
    @Override public String name() { return "Dependency convergence"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.dependencyConvergence; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        Map<String, Set<String>> versions = new HashMap<>();
        for (Artifact a : ctx.dependencies()) {
            String key = a.getGroupId() + ":" + a.getArtifactId();
            versions.computeIfAbsent(key, k -> new TreeSet<>()).add(a.getVersion());
        }
        for (Map.Entry<String, Set<String>> e : versions.entrySet()) {
            if (e.getValue().size() > 1) {
                findings.add(Finding.builder()
                    .checkId(id())
                    .severity(Severity.ERROR)
                    .gav(e.getKey())
                    .message("multiple versions resolved: " + e.getValue())
                    .build());
            }
        }
        return findings;
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=DependencyConvergenceCheckTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/adapter/DependencyConvergenceCheck.java \
        src/test/java/com/wlami/supplychain/check/adapter/DependencyConvergenceCheckTest.java
git commit -m "feat(check): add DependencyConvergenceCheck"
```

---

## Phase 8: PGP

### Task 20: `PgpKeysMap` parser

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/pgp/PgpKeysMap.java`
- Test: `src/test/java/com/wlami/supplychain/check/pgp/PgpKeysMapTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check.pgp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class PgpKeysMapTest {

    @Test
    void resolvesExactGavToFingerprint(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("keys.list");
        Files.writeString(f, "com.example:foo:1.0.0 = 0xABCDEF1234567890ABCDEF1234567890ABCDEF12\n");
        PgpKeysMap m = PgpKeysMap.read(f);
        Optional<String> fp = m.fingerprintFor("com.example", "foo", "1.0.0");
        assertThat(fp).contains("ABCDEF1234567890ABCDEF1234567890ABCDEF12");
    }

    @Test
    void supportsGlobPatterns(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("keys.list");
        Files.writeString(f, "com.example:* = 0x1111111111111111111111111111111111111111\n");
        PgpKeysMap m = PgpKeysMap.read(f);
        assertThat(m.fingerprintFor("com.example", "bar", "9.9.9")).contains("1111111111111111111111111111111111111111");
    }

    @Test
    void ignoresCommentsAndBlankLines(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("keys.list");
        Files.writeString(f, "# header\n\ng:a:v = 0xAAAA111111111111111111111111111111111111\n");
        PgpKeysMap m = PgpKeysMap.read(f);
        assertThat(m.fingerprintFor("g", "a", "v")).isPresent();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=PgpKeysMapTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check.pgp;

import com.wlami.supplychain.config.GavPattern;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PgpKeysMap {

    public static final PgpKeysMap EMPTY = new PgpKeysMap(Collections.emptyList());

    public static final class Entry {
        final GavPattern pattern;
        final String fingerprint;
        Entry(GavPattern p, String fp) { this.pattern = p; this.fingerprint = fp; }
    }

    private final List<Entry> entries;
    private PgpKeysMap(List<Entry> entries) { this.entries = entries; }

    public static PgpKeysMap read(Path file) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String left = line.substring(0, eq).trim();
            String right = line.substring(eq + 1).trim();
            if (right.startsWith("0x")) right = right.substring(2);
            entries.add(new Entry(GavPattern.parse(left), right.toUpperCase()));
        }
        return new PgpKeysMap(entries);
    }

    public Optional<String> fingerprintFor(String g, String a, String v) {
        for (Entry e : entries) {
            if (e.pattern.matches(g, a, v)) return Optional.of(e.fingerprint);
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=PgpKeysMapTest`
Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/pgp/PgpKeysMap.java \
        src/test/java/com/wlami/supplychain/check/pgp/PgpKeysMapTest.java
git commit -m "feat(pgp): add PgpKeysMap reader"
```

---

### Task 21: `KeyCache`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/pgp/KeyCache.java`
- Test: `src/test/java/com/wlami/supplychain/check/pgp/KeyCacheTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.check.pgp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class KeyCacheTest {

    @Test
    void roundTripsKeyBytes(@TempDir Path tmp) throws Exception {
        KeyCache c = new KeyCache(tmp);
        byte[] bytes = new byte[] {1, 2, 3, 4};
        c.put("ABCDEF12", bytes);
        Optional<byte[]> got = c.get("ABCDEF12");
        assertThat(got).isPresent();
        assertThat(got.get()).containsExactly(bytes);
    }

    @Test
    void missingKeyReturnsEmpty(@TempDir Path tmp) throws Exception {
        assertThat(new KeyCache(tmp).get("DEADBEEF")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=KeyCacheTest`
Expected: compile failure.

- [ ] **Step 3: Implement**

```java
package com.wlami.supplychain.check.pgp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class KeyCache {
    private final Path root;

    public KeyCache(Path root) throws IOException {
        this.root = root;
        Files.createDirectories(root);
    }

    public Optional<byte[]> get(String fingerprint) throws IOException {
        Path f = root.resolve(fingerprint.toUpperCase() + ".asc");
        if (!Files.exists(f)) return Optional.empty();
        return Optional.of(Files.readAllBytes(f));
    }

    public void put(String fingerprint, byte[] bytes) throws IOException {
        Files.write(root.resolve(fingerprint.toUpperCase() + ".asc"), bytes);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=KeyCacheTest`
Expected: 2 passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/pgp/KeyCache.java \
        src/test/java/com/wlami/supplychain/check/pgp/KeyCacheTest.java
git commit -m "feat(pgp): add file-backed KeyCache"
```

---

### Task 22: `KeyserverClient`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/pgp/KeyserverClient.java`

- [ ] **Step 1: Implement**

```java
package com.wlami.supplychain.check.pgp;

import com.wlami.supplychain.source.HttpClientFactory;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class KeyserverClient {

    private final String baseUrl;
    private final HttpClient http;

    public KeyserverClient(String baseUrl) { this(baseUrl, HttpClientFactory.create()); }
    public KeyserverClient(String baseUrl, HttpClient http) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = http;
    }

    /** Returns the ASCII-armored public key bytes for the given fingerprint. */
    public byte[] fetchKey(String fingerprint) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/pks/lookup?op=get&options=mr&search=0x" + fingerprint);
        HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Keyserver returned HTTP " + resp.statusCode() + " for " + fingerprint);
        }
        return resp.body();
    }
}
```

- [ ] **Step 2: Build**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/pgp/KeyserverClient.java
git commit -m "feat(pgp): add KeyserverClient"
```

---

### Task 23: `PgpVerifier`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/pgp/PgpVerifier.java`
- Test: `src/test/java/com/wlami/supplychain/check/pgp/PgpVerifierTest.java`

- [ ] **Step 1: Generate test fixtures**

Before writing the test, create a fixed test key pair + signed data. To keep the plan self-contained, the test fixture file `src/test/resources/pgp/` holds pre-generated:
- `test-public.asc` - ASCII-armored public key
- `payload.txt` - arbitrary content
- `payload.txt.asc` - detached signature over `payload.txt`

Generate locally once with GnuPG and check in (one-time setup):

```bash
mkdir -p src/test/resources/pgp
gpg --batch --pinentry-mode loopback --passphrase '' \
    --quick-generate-key 'supply-chain-test <test@example.com>' rsa4096
echo "test-payload-1" > src/test/resources/pgp/payload.txt
KEY_ID=$(gpg --list-keys --with-colons supply-chain-test \
  | awk -F: '/^pub/ {print $5; exit}')
gpg --armor --export "$KEY_ID" > src/test/resources/pgp/test-public.asc
gpg --batch --pinentry-mode loopback --passphrase '' \
    --detach-sign --armor \
    -o src/test/resources/pgp/payload.txt.asc \
    src/test/resources/pgp/payload.txt
```

Commit the three resource files; do NOT commit the GPG keyring.

- [ ] **Step 2: Write failing test**

```java
package com.wlami.supplychain.check.pgp;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import static org.assertj.core.api.Assertions.assertThat;

class PgpVerifierTest {

    @Test
    void verifiesValidSignature() throws Exception {
        try (InputStream key = getClass().getResourceAsStream("/pgp/test-public.asc");
             InputStream data = getClass().getResourceAsStream("/pgp/payload.txt");
             InputStream sig = getClass().getResourceAsStream("/pgp/payload.txt.asc")) {
            boolean ok = new PgpVerifier().verify(data.readAllBytes(), sig.readAllBytes(), key.readAllBytes());
            assertThat(ok).isTrue();
        }
    }

    @Test
    void rejectsTamperedData() throws Exception {
        try (InputStream key = getClass().getResourceAsStream("/pgp/test-public.asc");
             InputStream sig = getClass().getResourceAsStream("/pgp/payload.txt.asc")) {
            boolean ok = new PgpVerifier().verify("tampered\n".getBytes(), sig.readAllBytes(), key.readAllBytes());
            assertThat(ok).isFalse();
        }
    }
}
```

- [ ] **Step 3: Run test, verify fails**

Run: `mvn -q test -Dtest=PgpVerifierTest`
Expected: compile failure.

- [ ] **Step 4: Implement `PgpVerifier`**

```java
package com.wlami.supplychain.check.pgp;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public final class PgpVerifier {

    public boolean verify(byte[] data, byte[] armoredSignature, byte[] armoredPublicKey)
            throws IOException, PGPException {
        PGPSignature signature = readSignature(armoredSignature);
        if (signature == null) return false;
        PGPPublicKey key = findKey(armoredPublicKey, signature.getKeyID());
        if (key == null) return false;

        signature.init(new BcPGPContentVerifierBuilderProvider(), key);
        signature.update(data);
        return signature.verify();
    }

    private PGPSignature readSignature(byte[] armored) throws IOException {
        try (InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(armored))) {
            JcaPGPObjectFactory factory = new JcaPGPObjectFactory(in);
            Object o = factory.nextObject();
            if (o instanceof PGPCompressedData) {
                factory = new JcaPGPObjectFactory(((PGPCompressedData) o).getDataStream());
                o = factory.nextObject();
            }
            if (o instanceof PGPSignatureList) {
                PGPSignatureList list = (PGPSignatureList) o;
                if (list.isEmpty()) return null;
                return list.get(0);
            }
            return null;
        } catch (PGPException e) {
            throw new IOException(e);
        }
    }

    private PGPPublicKey findKey(byte[] armoredKey, long keyId) throws IOException, PGPException {
        try (InputStream in = new ArmoredInputStream(new ByteArrayInputStream(armoredKey))) {
            PGPPublicKeyRingCollection rings =
                new PGPPublicKeyRingCollection(in, new BcKeyFingerprintCalculator());
            for (Iterator<PGPPublicKeyRing> rIt = rings.getKeyRings(); rIt.hasNext(); ) {
                PGPPublicKeyRing ring = rIt.next();
                for (Iterator<PGPPublicKey> kIt = ring.getPublicKeys(); kIt.hasNext(); ) {
                    PGPPublicKey k = kIt.next();
                    if (k.getKeyID() == keyId) return k;
                }
            }
            return null;
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=PgpVerifierTest`
Expected: 2 passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/pgp/PgpVerifier.java \
        src/test/java/com/wlami/supplychain/check/pgp/PgpVerifierTest.java \
        src/test/resources/pgp/
git commit -m "feat(pgp): add PgpVerifier using BouncyCastle"
```

---

### Task 24: `PgpSignatureCheck`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/check/pgp/PgpSignatureCheck.java`

- [ ] **Step 1: Implement**

```java
package com.wlami.supplychain.check.pgp;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.Finding;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.source.HttpClientFactory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.project.MavenProject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Optional;

public final class PgpSignatureCheck implements Check {

    private final PgpVerifier verifier;
    private final KeyserverClient keyserver;
    private final KeyCache keyCache;
    private final HttpClient http;

    public PgpSignatureCheck(PgpVerifier verifier, KeyserverClient keyserver, KeyCache keyCache) {
        this.verifier = verifier;
        this.keyserver = keyserver;
        this.keyCache = keyCache;
        this.http = HttpClientFactory.create();
    }

    @Override public String id() { return "pgpSignature"; }
    @Override public String name() { return "PGP signature verification"; }
    @Override public boolean isEnabled(CheckContext ctx) { return ctx.config().checks.pgpSignature; }

    @Override
    public Findings run(CheckContext ctx) {
        Findings findings = new Findings();
        PgpKeysMap keysMap = loadKeysMap(ctx);

        for (Artifact a : ctx.dependencies()) {
            Path local = a.getFile() == null ? null : a.getFile().toPath();
            if (local == null || !Files.exists(local)) continue;

            Optional<byte[]> sig = downloadSignature(ctx.project(), a);
            if (sig.isEmpty()) {
                findings.add(finding(a, "missing .asc signature on remote", Severity.ERROR));
                continue;
            }
            try {
                byte[] data = Files.readAllBytes(local);
                long keyId = readKeyId(sig.get());
                byte[] keyBytes = resolveKey(keyId, keysMap, a);
                if (keyBytes == null) {
                    findings.add(finding(a, "could not resolve PGP public key", Severity.ERROR));
                    continue;
                }
                if (!verifier.verify(data, sig.get(), keyBytes)) {
                    findings.add(finding(a, "PGP signature did not verify", Severity.ERROR));
                }
            } catch (Exception e) {
                findings.add(finding(a, "PGP verification error: " + e.getMessage(), Severity.ERROR));
            }
        }
        return findings;
    }

    private static Finding finding(Artifact a, String msg, Severity sev) {
        return Finding.builder()
            .checkId("pgpSignature")
            .severity(sev)
            .gav(a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion())
            .message(msg)
            .build();
    }

    private PgpKeysMap loadKeysMap(CheckContext ctx) {
        String path = ctx.config().pgpKeysMap;
        if (path == null) return PgpKeysMap.EMPTY;
        try { return PgpKeysMap.read(Paths.get(path)); }
        catch (Exception e) { return PgpKeysMap.EMPTY; }
    }

    private Optional<byte[]> downloadSignature(MavenProject project, Artifact a) {
        for (ArtifactRepository repo : project.getRemoteArtifactRepositories()) {
            String base = repo.getUrl();
            if (!base.endsWith("/")) base += "/";
            String url = base + a.getGroupId().replace('.', '/') + "/"
                + a.getArtifactId() + "/" + a.getVersion() + "/"
                + a.getArtifactId() + "-" + a.getVersion() + ".jar.asc";
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() / 100 == 2) return Optional.of(resp.body());
            } catch (Exception ignored) {}
        }
        return Optional.empty();
    }

    private byte[] resolveKey(long keyId, PgpKeysMap keysMap, Artifact a) throws Exception {
        Optional<String> pinned = keysMap.fingerprintFor(a.getGroupId(), a.getArtifactId(), a.getVersion());
        String fp = pinned.orElse(String.format("%016X", keyId));
        Optional<byte[]> cached = keyCache.get(fp);
        if (cached.isPresent()) return cached.get();
        byte[] bytes = keyserver.fetchKey(fp);
        keyCache.put(fp, bytes);
        return bytes;
    }

    /** Reads the issuing key id from the signature without verifying. */
    private long readKeyId(byte[] armoredSignature) throws Exception {
        org.bouncycastle.openpgp.PGPSignature sig = readSig(armoredSignature);
        return sig == null ? 0L : sig.getKeyID();
    }

    private org.bouncycastle.openpgp.PGPSignature readSig(byte[] armored) throws Exception {
        try (java.io.InputStream in = org.bouncycastle.openpgp.PGPUtil.getDecoderStream(
                new java.io.ByteArrayInputStream(armored))) {
            org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory f =
                new org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory(in);
            Object o = f.nextObject();
            if (o instanceof org.bouncycastle.openpgp.PGPCompressedData) {
                f = new org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory(
                    ((org.bouncycastle.openpgp.PGPCompressedData) o).getDataStream());
                o = f.nextObject();
            }
            if (o instanceof org.bouncycastle.openpgp.PGPSignatureList) {
                org.bouncycastle.openpgp.PGPSignatureList l =
                    (org.bouncycastle.openpgp.PGPSignatureList) o;
                return l.isEmpty() ? null : l.get(0);
            }
            return null;
        }
    }
}
```

- [ ] **Step 2: Build**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/pgp/PgpSignatureCheck.java
git commit -m "feat(pgp): add PgpSignatureCheck"
```

---

## Phase 9: SARIF reporter

### Task 25: `SarifReporter`

**Files:**
- Create: `src/main/java/com/wlami/supplychain/report/sarif/SarifModel.java`
- Create: `src/main/java/com/wlami/supplychain/report/sarif/InputLocationResolver.java`
- Create: `src/main/java/com/wlami/supplychain/report/SarifReporter.java`
- Test: `src/test/java/com/wlami/supplychain/report/SarifReporterTest.java`

- [ ] **Step 1: Write failing test**

```java
package com.wlami.supplychain.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.wlami.supplychain.check.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class SarifReporterTest {

    @Test
    void writesValidSarifMatchingOfficialSchema(@TempDir Path tmp) throws Exception {
        Findings f = new Findings();
        f.add(Finding.builder()
            .checkId("minReleaseAge").severity(Severity.ERROR)
            .gav("com.example:foo:1.0.0")
            .message("too young")
            .property("publishedAt", "2026-05-13T12:00:00Z")
            .pomLine(42)
            .build());

        Path out = tmp.resolve("out.sarif");
        new SarifReporter(out, "supply-chain-maven-plugin", "0.1.0").write(f);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode written = mapper.readTree(Files.readAllBytes(out));
        assertThat(written.path("version").asText()).isEqualTo("2.1.0");
        assertThat(written.at("/runs/0/tool/driver/name").asText()).isEqualTo("supply-chain-maven-plugin");
        assertThat(written.at("/runs/0/results/0/ruleId").asText()).isEqualTo("minReleaseAge");

        try (InputStream schemaIn = getClass().getResourceAsStream("/sarif/sarif-2.1.0-schema.json")) {
            JsonSchema schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(schemaIn);
            Set<com.networknt.schema.ValidationMessage> errors = schema.validate(written);
            assertThat(errors).isEmpty();
        }
    }
}
```

Place the SARIF 2.1.0 schema at `src/test/resources/sarif/sarif-2.1.0-schema.json` (download from https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json).

- [ ] **Step 2: Run test, verify fails**

Run: `mvn -q test -Dtest=SarifReporterTest`
Expected: compile failure.

- [ ] **Step 3: Implement `InputLocationResolver`**

```java
package com.wlami.supplychain.report.sarif;

import com.wlami.supplychain.report.Finding;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InputLocationResolver {

    public Map<String, Object> resolve(Finding f, String pomUri) {
        Map<String, Object> loc = new LinkedHashMap<>();
        Map<String, Object> physicalLoc = new LinkedHashMap<>();
        Map<String, Object> artifactLoc = new LinkedHashMap<>();
        artifactLoc.put("uri", pomUri);
        physicalLoc.put("artifactLocation", artifactLoc);
        if (f.pomLine() != null) {
            Map<String, Object> region = new LinkedHashMap<>();
            region.put("startLine", f.pomLine());
            physicalLoc.put("region", region);
        }
        loc.put("physicalLocation", physicalLoc);
        return loc;
    }
}
```

- [ ] **Step 4: Implement `SarifReporter`**

```java
package com.wlami.supplychain.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wlami.supplychain.check.Severity;
import com.wlami.supplychain.report.sarif.InputLocationResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SarifReporter implements Reporter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path out;
    private final String toolName;
    private final String toolVersion;

    public SarifReporter(Path out, String toolName, String toolVersion) {
        this.out = out;
        this.toolName = toolName;
        this.toolVersion = toolVersion;
    }

    @Override
    public void write(Findings findings) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        doc.put("version", "2.1.0");

        Map<String, Object> run = new LinkedHashMap<>();
        Map<String, Object> tool = new LinkedHashMap<>();
        Map<String, Object> driver = new LinkedHashMap<>();
        driver.put("name", toolName);
        driver.put("version", toolVersion);
        driver.put("informationUri", "https://github.com/wlami/supply-chain-maven-plugin");
        driver.put("rules", buildRules(findings));
        tool.put("driver", driver);
        run.put("tool", tool);

        run.put("results", buildResults(findings));
        doc.put("runs", List.of(run));

        try {
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.write(out, MAPPER.writeValueAsBytes(doc));
        } catch (IOException e) {
            throw new RuntimeException("Failed to write SARIF: " + out, e);
        }
    }

    private List<Map<String, Object>> buildRules(Findings findings) {
        Map<String, Map<String, Object>> rules = new TreeMap<>();
        for (Finding f : findings.all()) {
            rules.computeIfAbsent(f.checkId(), id -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", id);
                r.put("name", id);
                r.put("shortDescription", Map.of("text", id));
                r.put("helpUri", "https://github.com/wlami/supply-chain-maven-plugin#" + id);
                return r;
            });
        }
        return new ArrayList<>(rules.values());
    }

    private List<Map<String, Object>> buildResults(Findings findings) {
        InputLocationResolver resolver = new InputLocationResolver();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Finding f : findings.all()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ruleId", f.checkId());
            r.put("level", level(f.severity()));
            r.put("message", Map.of("text", "[" + f.gav() + "] " + f.message()));
            r.put("locations", List.of(resolver.resolve(f, "pom.xml")));
            Map<String, Object> props = new LinkedHashMap<>();
            props.put("gav", f.gav());
            props.putAll(f.properties());
            r.put("properties", props);
            r.put("partialFingerprints", Map.of("gavCheckId", f.fingerprint()));
            out.add(r);
        }
        return out;
    }

    private static String level(Severity s) {
        switch (s) {
            case ERROR: return "error";
            case WARNING: return "warning";
            default: return "note";
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest=SarifReporterTest`
Expected: 1 passed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/wlami/supplychain/report/sarif/ \
        src/main/java/com/wlami/supplychain/report/SarifReporter.java \
        src/test/java/com/wlami/supplychain/report/SarifReporterTest.java \
        src/test/resources/sarif/sarif-2.1.0-schema.json
git commit -m "feat(report): add SARIF 2.1.0 reporter"
```

---

## Phase 10: Wire `CheckMojo`, `ReportMojo`, `RefreshCacheMojo`

### Task 26: Implement `CheckMojo` and `ReportMojo`

**Files:**
- Modify: `src/main/java/com/wlami/supplychain/mojo/CheckMojo.java`
- Create: `src/main/java/com/wlami/supplychain/mojo/AbstractSupplyChainMojo.java`
- Create: `src/main/java/com/wlami/supplychain/mojo/ReportMojo.java`
- Create: `src/main/java/com/wlami/supplychain/mojo/RefreshCacheMojo.java`
- Create: `src/main/java/com/wlami/supplychain/check/CheckRegistry.java`

- [ ] **Step 1: Implement `CheckRegistry`**

```java
package com.wlami.supplychain.check;

import com.wlami.supplychain.check.adapter.BannedDependenciesCheck;
import com.wlami.supplychain.check.adapter.DependencyConvergenceCheck;
import com.wlami.supplychain.check.adapter.RequireReleaseDepsCheck;
import com.wlami.supplychain.check.pgp.KeyCache;
import com.wlami.supplychain.check.pgp.KeyserverClient;
import com.wlami.supplychain.check.pgp.PgpSignatureCheck;
import com.wlami.supplychain.check.pgp.PgpVerifier;
import com.wlami.supplychain.source.CentralSearchClient;
import com.wlami.supplychain.source.ReleaseDateCache;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

public final class CheckRegistry {

    public static List<Check> defaults(Path cacheRoot) throws IOException {
        CentralSearchClient central = new CentralSearchClient("https://search.maven.org");
        ReleaseDateCache releaseDateCache = new ReleaseDateCache(cacheRoot.resolve("release-dates.json"));
        KeyserverClient keyserver = new KeyserverClient("https://keyserver.ubuntu.com");
        KeyCache keyCache = new KeyCache(cacheRoot.resolve("pgp-keys"));

        List<Check> checks = new ArrayList<>();
        checks.add(new MinReleaseAgeCheck(central, Clock.systemUTC()));
        checks.add(new RequireExactVersionsCheck());
        checks.add(new RepositoryAllowlistCheck());
        checks.add(new ChecksumPolicyCheck());
        checks.add(new BaselineCheck());
        checks.add(new RequireReleaseDepsCheck());
        checks.add(new BannedDependenciesCheck());
        checks.add(new DependencyConvergenceCheck());
        checks.add(new PgpSignatureCheck(new PgpVerifier(), keyserver, keyCache));
        return checks;
    }
}
```

Note: `MinReleaseAgeCheck` does not currently take the `ReleaseDateCache`. Update its constructor to accept one and consult it before hitting Central:

```java
public MinReleaseAgeCheck(CentralSearchClient client, Clock clock, ReleaseDateCache cache) {
    this.client = client;
    this.clock = clock;
    this.cache = cache;
}
```

And in `run`:

```java
String gav = a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion();
Optional<Instant> cached = cache.get(gav);
Optional<Instant> published = cached.isPresent() ? cached : client.fetchReleaseDate(a.getGroupId(), a.getArtifactId(), a.getVersion());
if (published.isEmpty()) continue;
if (cached.isEmpty()) { cache.put(gav, published.get()); cache.flush(); }
```

Update `MinReleaseAgeCheckTest` to pass a temp-dir-backed cache, then re-run that test.

- [ ] **Step 2: Implement `AbstractSupplyChainMojo`**

```java
package com.wlami.supplychain.mojo;

import com.wlami.supplychain.check.Check;
import com.wlami.supplychain.check.CheckContext;
import com.wlami.supplychain.check.CheckRegistry;
import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.ConsoleReporter;
import com.wlami.supplychain.report.Findings;
import com.wlami.supplychain.report.SarifReporter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class AbstractSupplyChainMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true) protected MavenProject project;
    @Parameter(defaultValue = "${session}", readonly = true) protected MavenSession session;

    @Parameter(property = "supply-chain.minReleaseAge", defaultValue = "P3D")
    protected String minReleaseAge;

    @Parameter protected List<String> minReleaseAgeExclusions;
    @Parameter protected List<String> repositoryAllowlist;
    @Parameter protected List<String> bannedDependencies;
    @Parameter protected String pgpKeysMap;
    @Parameter protected List<String> excludes;
    @Parameter protected List<com.wlami.supplychain.config.Override> overrides;
    @Parameter protected com.wlami.supplychain.config.ChecksConfig checks;
    @Parameter(defaultValue = "${project.build.directory}/supply-chain.sarif")
    protected String sarifOutput;
    @Parameter(defaultValue = "FAIL") protected String onViolation;
    @Parameter(defaultValue = "FAIL") protected String onNetworkError;

    protected PluginConfig buildConfig() {
        PluginConfig c = new PluginConfig();
        if (minReleaseAge != null) c.minReleaseAge = minReleaseAge;
        if (minReleaseAgeExclusions != null) c.minReleaseAgeExclusions = minReleaseAgeExclusions;
        if (repositoryAllowlist != null && !repositoryAllowlist.isEmpty()) c.repositoryAllowlist = repositoryAllowlist;
        if (bannedDependencies != null) c.bannedDependencies = bannedDependencies;
        if (pgpKeysMap != null) c.pgpKeysMap = pgpKeysMap;
        if (excludes != null) c.excludes = excludes;
        if (overrides != null) c.overrides = overrides;
        if (checks != null) c.checks = checks;
        if (sarifOutput != null) c.sarifOutput = sarifOutput;
        if (onViolation != null) c.onViolation = onViolation;
        if (onNetworkError != null) c.onNetworkError = onNetworkError;
        return c;
    }

    protected Findings runAllChecks(PluginConfig cfg) throws MojoExecutionException {
        Path cacheRoot = Paths.get(System.getProperty("user.home"), ".m2", "repository", ".supply-chain-cache");
        Findings agg = new Findings();
        try {
            List<Check> checks = CheckRegistry.defaults(cacheRoot);
            CheckContext ctx = new CheckContext(project, session, cfg, project.getArtifacts());
            for (Check c : checks) {
                if (!c.isEnabled(ctx)) continue;
                getLog().debug("Running check: " + c.id());
                agg.addAll(c.run(ctx));
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failure running supply-chain checks", e);
        }
        return agg;
    }

    protected void writeReports(Findings findings, PluginConfig cfg) {
        new ConsoleReporter(System.out).write(findings);
        if (cfg.sarifOutput != null && !cfg.sarifOutput.isEmpty()) {
            new SarifReporter(Paths.get(cfg.sarifOutput),
                "supply-chain-maven-plugin", pluginVersion()).write(findings);
        }
    }

    private String pluginVersion() {
        return getClass().getPackage().getImplementationVersion() != null
            ? getClass().getPackage().getImplementationVersion() : "0.1.0";
    }
}
```

- [ ] **Step 3: Rewrite `CheckMojo`**

```java
package com.wlami.supplychain.mojo;

import com.wlami.supplychain.config.PluginConfig;
import com.wlami.supplychain.report.Findings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
    name = "check",
    defaultPhase = LifecyclePhase.VALIDATE,
    requiresDependencyResolution = ResolutionScope.TEST,
    threadSafe = true
)
public class CheckMojo extends AbstractSupplyChainMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        PluginConfig cfg = buildConfig();
        Findings findings = runAllChecks(cfg);
        writeReports(findings, cfg);
        if (findings.hasErrors() && "FAIL".equalsIgnoreCase(cfg.onViolation)) {
            throw new MojoFailureException("supply-chain check failed: " + findings.errors().size() + " error(s)");
        }
    }
}
```

- [ ] **Step 4: Implement `ReportMojo`**

```java
package com.wlami.supplychain.mojo;

import com.wlami.supplychain.config.PluginConfig;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "report", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class ReportMojo extends AbstractSupplyChainMojo {

    @Override
    public void execute() throws MojoExecutionException {
        PluginConfig cfg = buildConfig();
        writeReports(runAllChecks(cfg), cfg);
    }
}
```

- [ ] **Step 5: Implement `RefreshCacheMojo`**

```java
package com.wlami.supplychain.mojo;

import com.wlami.supplychain.source.ReleaseDateCache;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import java.nio.file.Paths;

@Mojo(name = "refresh-cache", threadSafe = true)
public class RefreshCacheMojo extends AbstractMojo {

    @Override
    public void execute() throws MojoExecutionException {
        java.nio.file.Path cacheRoot = Paths.get(System.getProperty("user.home"),
            ".m2", "repository", ".supply-chain-cache");
        ReleaseDateCache cache = new ReleaseDateCache(cacheRoot.resolve("release-dates.json"));
        cache.clear();
        cache.flush();
        getLog().info("Cleared release-date cache at " + cacheRoot);
    }
}
```

- [ ] **Step 6: Build and run all tests**

Run: `mvn -q clean install -DskipITs=true`
Expected: BUILD SUCCESS, all unit tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/wlami/supplychain/check/CheckRegistry.java \
        src/main/java/com/wlami/supplychain/mojo/AbstractSupplyChainMojo.java \
        src/main/java/com/wlami/supplychain/mojo/CheckMojo.java \
        src/main/java/com/wlami/supplychain/mojo/ReportMojo.java \
        src/main/java/com/wlami/supplychain/mojo/RefreshCacheMojo.java \
        src/main/java/com/wlami/supplychain/check/MinReleaseAgeCheck.java \
        src/test/java/com/wlami/supplychain/check/MinReleaseAgeCheckTest.java
git commit -m "feat(mojo): wire check, report, refresh-cache mojos with full registry"
```

---

## Phase 11: Integration tests

### Task 27: IT bootstrap (`pass-default-config`)

**Files:**
- Create: `src/it/settings.xml`
- Create: `src/it/pass-default-config/pom.xml`
- Create: `src/it/pass-default-config/invoker.properties`
- Create: `src/it/pass-default-config/verify.groovy`

- [ ] **Step 1: Write IT settings**

`src/it/settings.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings>
  <profiles>
    <profile>
      <id>it</id>
      <repositories>
        <repository>
          <id>local-it</id>
          <url>file://@localRepositoryUrl@</url>
        </repository>
        <repository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles><activeProfile>it</activeProfile></activeProfiles>
</settings>
```

- [ ] **Step 2: Write passing IT project**

`src/it/pass-default-config/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.wlami.it</groupId>
  <artifactId>pass-default-config</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>
  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-api</artifactId>
      <version>2.0.13</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>com.wlami</groupId>
        <artifactId>supply-chain-maven-plugin</artifactId>
        <version>@project.version@</version>
        <executions><execution><goals><goal>check</goal></goals></execution></executions>
        <configuration>
          <checks>
            <pgpSignature>false</pgpSignature>
            <dependencyConvergence>false</dependencyConvergence>
          </checks>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Note: PGP is disabled in this IT to keep it network-light; PGP coverage is exercised in unit tests + the `fail-unsigned` IT.

- [ ] **Step 3: Write invoker config**

`src/it/pass-default-config/invoker.properties`:

```properties
invoker.goals = clean verify
invoker.buildResult = success
```

- [ ] **Step 4: Write verifier**

`src/it/pass-default-config/verify.groovy`:

```groovy
File log = new File(basedir, "build.log")
assert log.exists()
assert log.text.contains("supply-chain check passed")

File sarif = new File(basedir, "target/supply-chain.sarif")
assert sarif.exists()
assert sarif.text.contains('"version" : "2.1.0"')
```

- [ ] **Step 5: Run ITs**

Run: `mvn -q clean install`
Expected: integration-test phase runs the IT; BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add src/it/settings.xml src/it/pass-default-config/
git commit -m "test(it): add passing default-config integration test"
```

---

### Task 28: Failing ITs (one per check)

**Files:**
- Create: `src/it/fail-min-age/{pom.xml,invoker.properties,verify.groovy}`
- Create: `src/it/fail-banned/{pom.xml,invoker.properties,verify.groovy}`
- Create: `src/it/fail-version-range/{pom.xml,invoker.properties,verify.groovy}`
- Create: `src/it/fail-repo-allowlist/{pom.xml,invoker.properties,verify.groovy}`
- Create: `src/it/fail-baseline-new-artifact/{pom.xml,invoker.properties,verify.groovy,.supply-chain-baseline.json}`
- Create: `src/it/pass-baseline/{pom.xml,invoker.properties,verify.groovy,.supply-chain-baseline.json}`

Pattern for each `fail-*` IT:
- `invoker.properties`: `invoker.buildResult = failure` and `invoker.goals = clean verify`
- `verify.groovy`: assert `build.log` contains the expected check id and the failure message

Example - `src/it/fail-banned/pom.xml` (the others follow the same template):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.wlami.it</groupId>
  <artifactId>fail-banned</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>
  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
  </properties>
  <dependencies>
    <dependency>
      <groupId>commons-collections</groupId>
      <artifactId>commons-collections</artifactId>
      <version>3.2.1</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>com.wlami</groupId>
        <artifactId>supply-chain-maven-plugin</artifactId>
        <version>@project.version@</version>
        <executions><execution><goals><goal>check</goal></goals></execution></executions>
        <configuration>
          <bannedDependencies>
            <bannedDependency>commons-collections:commons-collections</bannedDependency>
          </bannedDependencies>
          <checks>
            <pgpSignature>false</pgpSignature>
          </checks>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

`src/it/fail-banned/invoker.properties`:

```properties
invoker.goals = clean verify
invoker.buildResult = failure
```

`src/it/fail-banned/verify.groovy`:

```groovy
File log = new File(basedir, "build.log")
assert log.exists()
assert log.text.contains("[bannedDependencies]")
assert log.text.contains("commons-collections:commons-collections")
```

For each remaining IT (`fail-min-age`, `fail-version-range`, `fail-repo-allowlist`, `fail-baseline-new-artifact`, `pass-baseline`), follow the same template:

- `fail-min-age`: depend on a brand-new release published less than 3 days ago; OR override the system clock via plugin config (preferred for deterministic ITs). To keep ITs deterministic, instead of relying on a real young artifact, this IT sets a deliberately-impossible threshold:

```xml
<configuration>
  <minReleaseAge>P3650D</minReleaseAge>
  <checks>
    <pgpSignature>false</pgpSignature>
  </checks>
</configuration>
```

(10-year threshold guarantees any real dep fails.)

- `fail-version-range`: declare a dep using `<version>[1.0,2.0)</version>`.
- `fail-repo-allowlist`: declare an extra `<repository>` pointing at a non-Central URL.
- `fail-baseline-new-artifact`: ship `.supply-chain-baseline.json` listing only one artifact, declare a different artifact in `pom.xml`.
- `pass-baseline`: ship baseline matching the declared artifact exactly.

- [ ] **Step 1: Create `fail-banned` IT (as shown above)**
- [ ] **Step 2: Create `fail-min-age` IT** with the 10-year threshold trick
- [ ] **Step 3: Create `fail-version-range` IT**
- [ ] **Step 4: Create `fail-repo-allowlist` IT**
- [ ] **Step 5: Create `pass-baseline` IT**
- [ ] **Step 6: Create `fail-baseline-new-artifact` IT**

For each, write the three files (`pom.xml`, `invoker.properties`, `verify.groovy`) following the pattern.

- [ ] **Step 7: Run all ITs**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS. All ITs hit their expected pass/fail state.

- [ ] **Step 8: Commit**

```bash
git add src/it/
git commit -m "test(it): add failing ITs for each check (banned, min-age, version-range, repo, baseline)"
```

---

### Task 29: `fail-unsigned` IT (network-touching, opt-in profile)

**Files:**
- Create: `src/it/fail-unsigned/{pom.xml,invoker.properties,verify.groovy}`
- Modify: `pom.xml` (add `network-its` profile gating this IT)

- [ ] **Step 1: Write IT**

`src/it/fail-unsigned/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.wlami.it</groupId>
  <artifactId>fail-unsigned</artifactId>
  <version>1.0.0</version>
  <packaging>jar</packaging>
  <properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
  </properties>
  <dependencies>
    <!-- Known-unsigned artifact (historical artifact lacking .asc). -->
    <dependency>
      <groupId>com.wlami</groupId>
      <artifactId>placeholder-unsigned</artifactId>
      <version>1.0.0</version>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>com.wlami</groupId>
        <artifactId>supply-chain-maven-plugin</artifactId>
        <version>@project.version@</version>
        <executions><execution><goals><goal>check</goal></goals></execution></executions>
        <configuration>
          <checks>
            <minReleaseAge>false</minReleaseAge>
          </checks>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

Note: a real Central artifact known to lack signatures must be chosen at implementation time; an internal test artifact published to a local repo via the build is preferred.

`src/it/fail-unsigned/invoker.properties`:

```properties
invoker.goals = clean verify
invoker.buildResult = failure
invoker.profiles = network-its
```

`src/it/fail-unsigned/verify.groovy`:

```groovy
File log = new File(basedir, "build.log")
assert log.exists()
assert log.text.contains("[pgpSignature]")
```

- [ ] **Step 2: Gate IT behind `network-its` profile in main `pom.xml`**

Add a profile that includes the network-touching invoker patterns:

```xml
<profiles>
  <profile>
    <id>network-its</id>
    <build>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-invoker-plugin</artifactId>
          <configuration>
            <pomIncludes>
              <pomInclude>fail-unsigned/pom.xml</pomInclude>
            </pomIncludes>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```

The default IT run excludes `fail-unsigned` so CI without network access still passes.

- [ ] **Step 3: Run with profile**

Run: `mvn -q clean install -Pnetwork-its`
Expected: BUILD SUCCESS, fail-unsigned IT fires.

- [ ] **Step 4: Commit**

```bash
git add src/it/fail-unsigned/ pom.xml
git commit -m "test(it): add fail-unsigned IT under network-its profile"
```

---

## Phase 12: Docs and release prep

### Task 30: Write `README.md`

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write README**

```markdown
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

Empty configuration = full hardening. Run `mvn validate` to execute.

## Excluding your own artifacts from `minReleaseAge`

Publishing your own artifact to Central and wanting to consume it immediately? Add it to `<minReleaseAgeExclusions>`:

```xml
<configuration>
  <minReleaseAgeExclusions>
    <exclusion>com.wlami:my-artifact</exclusion>
  </minReleaseAgeExclusions>
</configuration>
```

Pin to `groupId:artifactId`. Avoid `groupId:*` - it matches future artifacts you have not published yet. Combine with `<repositoryAllowlist>` so the exempted artifact still has to come from a trusted repo.

## Configuration reference

See `docs/superpowers/specs/2026-05-14-supply-chain-maven-plugin-design.md`.

## SARIF output

`target/supply-chain.sarif` is always produced. Upload to GitHub Code Scanning:

```yaml
- uses: github/codeql-action/upload-sarif@v3
  with: { sarif_file: target/supply-chain.sarif }
```

## Goals

- `supply-chain:check` - run all enabled checks (bound to `validate`).
- `supply-chain:report` - run checks without failing the build.
- `supply-chain:dump-baseline` - write a baseline file of currently resolved deps.
- `supply-chain:refresh-cache` - clear the release-date cache.

## License

Apache-2.0.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README"
```

---

### Task 31: Final verification + push

**Files:** (none new)

- [ ] **Step 1: Run the full local build**

Run: `mvn -q clean install`
Expected: BUILD SUCCESS. All unit tests + non-network ITs pass.

- [ ] **Step 2: Optional - run network ITs**

Run: `mvn -q clean install -Pnetwork-its`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Push branch**

Run: `git push`
Expected: pushed to `origin/main`.

---

## Self-Review

After writing the plan, re-verified the following:

1. **Spec coverage:** All checks in the spec map to tasks:
   - `minReleaseAge` → Task 12
   - `requireExactVersions` → Task 13
   - `repositoryAllowlist` → Task 14
   - `checksumStrict` → Task 15
   - `baseline` (+ `dump-baseline` mojo) → Task 16
   - `requireReleaseDeps` → Task 17
   - `bannedDependencies` → Task 18
   - `dependencyConvergence` → Task 19
   - `pgpSignature` (`PgpVerifier`, `KeyCache`, `KeyserverClient`, `PgpKeysMap`, `PgpSignatureCheck`) → Tasks 20-24
   - `CheckMojo`, `ReportMojo`, `RefreshCacheMojo` → Task 26
   - SARIF reporter → Task 25
   - Console reporter → Task 5
   - GAV patterns + overrides → Tasks 6, 7
   - Central API client + cache → Tasks 10, 11
   - All ITs → Tasks 27, 28, 29
   - README → Task 30

2. **Placeholders:** No "TBD"/"TODO"/"implement later". One deliberate flag: the `fail-unsigned` IT depends on a placeholder GAV; this is documented inline and made explicit (must pick a real unsigned artifact, or publish an internal test artifact during the build, at implementation time).

3. **Type consistency:** `Check.run` returns `Findings`; every check implementation conforms. `Finding` builder fields used consistently across tasks. `MinReleaseAgeCheck` constructor signature changes between Task 12 (test) and Task 26 (registry wiring) - Task 26 explicitly calls out the constructor update and the test update so the engineer doesn't miss it.

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-14-supply-chain-maven-plugin.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
