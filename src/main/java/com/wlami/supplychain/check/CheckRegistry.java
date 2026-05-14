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

    private CheckRegistry() {}

    public static List<Check> defaults(Path cacheRoot) throws IOException {
        CentralSearchClient central = new CentralSearchClient("https://search.maven.org");
        ReleaseDateCache releaseDateCache = new ReleaseDateCache(cacheRoot.resolve("release-dates.json"));
        KeyserverClient keyserver = new KeyserverClient("https://keyserver.ubuntu.com");
        KeyCache keyCache = new KeyCache(cacheRoot.resolve("pgp-keys"));

        List<Check> checks = new ArrayList<>();
        checks.add(new MinReleaseAgeCheck(central, Clock.systemUTC(), releaseDateCache));
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
