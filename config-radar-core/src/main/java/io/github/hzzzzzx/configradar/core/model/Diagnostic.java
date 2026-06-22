package io.github.hzzzzzx.configradar.core.model;

/** Tool diagnostic that explains partial scan failures or degraded analysis. */
public record Diagnostic(
    DiagnosticSeverity severity,
    String phase,
    String message,
    String componentId
) {
}
