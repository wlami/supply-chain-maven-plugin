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
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
        if (project == null) return Optional.empty();
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

    private long readKeyId(byte[] armoredSignature) throws Exception {
        try (InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(armoredSignature))) {
            JcaPGPObjectFactory f = new JcaPGPObjectFactory(in);
            Object o = f.nextObject();
            if (o instanceof PGPCompressedData) {
                f = new JcaPGPObjectFactory(((PGPCompressedData) o).getDataStream());
                o = f.nextObject();
            }
            if (o instanceof PGPSignatureList) {
                PGPSignatureList l = (PGPSignatureList) o;
                return l.isEmpty() ? 0L : l.get(0).getKeyID();
            }
            return 0L;
        }
    }
}
