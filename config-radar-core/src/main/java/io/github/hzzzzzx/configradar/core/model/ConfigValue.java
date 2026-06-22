package io.github.hzzzzzx.configradar.core.model;

import java.util.Objects;

/** Raw value plus a small optional type hint for review and downstream checks. */
public record ConfigValue(
    String raw,
    String normalized,
    ValueType type
) {
    public ConfigValue {
        Objects.requireNonNull(raw, "raw");
        type = type == null ? ValueType.UNKNOWN : type;
    }
}
