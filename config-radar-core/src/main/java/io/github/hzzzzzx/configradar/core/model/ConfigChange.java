package io.github.hzzzzzx.configradar.core.model;

/** Field-level change for one matched finding. */
public record ConfigChange(
    String key,
    String field,
    String oldValue,
    String newValue
) {
}
