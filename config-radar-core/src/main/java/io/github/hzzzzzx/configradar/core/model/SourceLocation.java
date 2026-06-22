package io.github.hzzzzzx.configradar.core.model;

import java.util.Objects;

/** Reviewable location evidence for a finding. */
public record SourceLocation(
    String path,
    Integer line,
    String symbol,
    SourceKind sourceKind,
    Scope scope
) {
    public SourceLocation {
        Objects.requireNonNull(path, "path");
        sourceKind = sourceKind == null ? SourceKind.UNKNOWN : sourceKind;
        scope = scope == null ? Scope.UNKNOWN : scope;
    }
}
