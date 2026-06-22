package io.github.hzzzzzx.configradar.core.model;

import java.util.List;

/** Diff between two inventories. */
public record ConfigDiff(
    String schemaVersion,
    DiffSummary summary,
    List<ConfigFinding> added,
    List<ConfigFinding> removed,
    List<ConfigChange> changed,
    List<UncertainFinding> uncertainChanged
) {
    public static final String SCHEMA_VERSION = "config-diff/v1";

    public ConfigDiff {
        schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        added = List.copyOf(added == null ? List.of() : added);
        removed = List.copyOf(removed == null ? List.of() : removed);
        changed = List.copyOf(changed == null ? List.of() : changed);
        uncertainChanged = List.copyOf(uncertainChanged == null ? List.of() : uncertainChanged);
        summary = summary == null
            ? new DiffSummary(added.size(), removed.size(), changed.size(), uncertainChanged.size())
            : summary;
    }
}
