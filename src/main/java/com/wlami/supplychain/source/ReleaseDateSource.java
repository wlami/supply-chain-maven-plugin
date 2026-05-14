package com.wlami.supplychain.source;

import java.time.Instant;
import java.util.Optional;

public interface ReleaseDateSource {
    Optional<Instant> fetchReleaseDate(String groupId, String artifactId, String version);
}
