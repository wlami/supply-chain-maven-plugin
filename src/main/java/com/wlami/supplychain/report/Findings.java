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
