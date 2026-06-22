package io.github.hzzzzzx.configradar.core.model;

/** Counts used by humans and downstream checks. */
public record InventorySummary(
    int keys,
    int findings,
    int uncertain,
    int checks,
    int diagnostics
) {
}
