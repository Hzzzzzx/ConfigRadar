package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.rule.ConfigRules;

/** Context passed to every pipeline component. */
public record ScanContext(
    ScanInput input,
    ScanOptions options,
    ConfigRules rules,
    FileIndex fileIndex
) {
}
