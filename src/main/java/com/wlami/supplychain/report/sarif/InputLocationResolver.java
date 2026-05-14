package com.wlami.supplychain.report.sarif;

import com.wlami.supplychain.report.Finding;
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
