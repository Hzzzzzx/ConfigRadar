package io.github.hzzzzzx.configradar.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Canonical inventory output. Serialized YAML keeps items and uncertain separated. */
public record ConfigInventory(
    String schemaVersion,
    ProjectInfo project,
    InventorySummary summary,
    List<ConfigFinding> items,
    List<UncertainFinding> uncertain,
    List<InventoryCheck> checks,
    List<Diagnostic> diagnostics
) {
    public static final String SCHEMA_VERSION = "config-inventory/v1";

    public ConfigInventory {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        project = project == null ? ProjectInfo.unknown() : project;
        items = List.copyOf(items == null ? List.of() : items);
        uncertain = List.copyOf(uncertain == null ? List.of() : uncertain);
        checks = List.copyOf(checks == null ? List.of() : checks);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        summary = summary == null
            ? new InventorySummary(0, items.size(), uncertain.size(), checks.size(), diagnostics.size())
            : summary;
    }

    public List<ScanFinding> allFindings() {
        var all = new ArrayList<ScanFinding>(items.size() + uncertain.size());
        all.addAll(items);
        all.addAll(uncertain);
        return List.copyOf(all);
    }

    public ConfigInventory withSummary(InventorySummary newSummary) {
        return new ConfigInventory(schemaVersion, project, Objects.requireNonNull(newSummary), items, uncertain, checks, diagnostics);
    }
}
