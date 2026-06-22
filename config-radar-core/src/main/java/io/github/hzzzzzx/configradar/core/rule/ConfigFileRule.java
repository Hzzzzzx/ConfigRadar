package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.scan.FileType;

/** Declarative rule for additional config file patterns. */
public record ConfigFileRule(
    String id,
    String pattern,
    FileType format,
    Scope scope
) implements ConfigRule {
    public ConfigFileRule {
        format = format == null ? FileType.UNKNOWN : format;
        scope = scope == null ? Scope.UNKNOWN : scope;
    }
}
