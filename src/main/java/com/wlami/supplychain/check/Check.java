package com.wlami.supplychain.check;

import com.wlami.supplychain.report.Findings;

public interface Check {
    String id();
    String name();
    boolean isEnabled(CheckContext ctx);
    Findings run(CheckContext ctx) throws Exception;
}
