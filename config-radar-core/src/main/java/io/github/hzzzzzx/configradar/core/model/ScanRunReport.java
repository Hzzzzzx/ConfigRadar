package io.github.hzzzzzx.configradar.core.model;

import java.util.List;

/** Optional sidecar report for performance metrics and diagnostics. */
public record ScanRunReport(
    String schemaVersion,
    List<PerformanceMetric> metrics,
    List<Diagnostic> diagnostics
) {
    public static final String SCHEMA_VERSION = "config-radar-run/v1";

    public ScanRunReport {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        metrics = List.copyOf(metrics == null ? List.of() : metrics);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
