package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.Diagnostic;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.PerformanceMetric;
import io.github.hzzzzzx.configradar.core.model.ProjectInfo;
import io.github.hzzzzzx.configradar.core.model.ScanRunReport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScanResultTest {
    @Test
    void defaultsAndProtectsRunDataLists() {
        var result = new ScanResult(emptyInventory(), null, null);

        assertEquals(List.of(), result.diagnostics());
        assertEquals(List.of(), result.metrics());
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().add(diagnostic()));
        assertThrows(UnsupportedOperationException.class, () -> result.metrics().add(metric()));
    }

    @Test
    void reportCopiesMetricsAndDiagnosticsToSidecarShape() {
        var diagnostics = new ArrayList<>(List.of(diagnostic()));
        var metrics = new ArrayList<>(List.of(metric()));

        var result = new ScanResult(emptyInventory(), diagnostics, metrics);
        diagnostics.clear();
        metrics.clear();
        var report = result.report();

        assertEquals(ScanRunReport.SCHEMA_VERSION, report.schemaVersion());
        assertEquals(List.of(metric()), report.metrics());
        assertEquals(List.of(diagnostic()), report.diagnostics());
        assertThrows(UnsupportedOperationException.class, () -> report.metrics().add(metric()));
    }

    private static ConfigInventory emptyInventory() {
        return new ConfigInventory(null, ProjectInfo.unknown(), null, null, null, null, null);
    }

    private static Diagnostic diagnostic() {
        return new Diagnostic(DiagnosticSeverity.WARNING, "detector", "boom", "test-detector");
    }

    private static PerformanceMetric metric() {
        return new PerformanceMetric("file-indexing", 12);
    }
}
