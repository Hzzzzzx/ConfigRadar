package io.github.hzzzzzx.configradar.core.model;

/** Non-blocking quality or risk check. */
public record InventoryCheck(
    String type,
    DiagnosticSeverity severity,
    String message,
    String key,
    SourceLocation source
) {
}
