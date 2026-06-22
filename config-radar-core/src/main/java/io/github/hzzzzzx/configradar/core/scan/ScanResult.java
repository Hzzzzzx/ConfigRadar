package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.Diagnostic;
import io.github.hzzzzzx.configradar.core.model.PerformanceMetric;
import io.github.hzzzzzx.configradar.core.model.ScanRunReport;
import java.util.List;

/** Result of one pipeline run. */
public record ScanResult(
    ConfigInventory inventory,
    List<Diagnostic> diagnostics,
    List<PerformanceMetric> metrics
) {
    public ScanResult {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        metrics = List.copyOf(metrics == null ? List.of() : metrics);
    }

    public ScanRunReport report() {
        return new ScanRunReport(ScanRunReport.SCHEMA_VERSION, metrics, diagnostics);
    }
}
