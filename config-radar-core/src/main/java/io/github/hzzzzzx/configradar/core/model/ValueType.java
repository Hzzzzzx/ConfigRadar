package io.github.hzzzzzx.configradar.core.model;

/** Lightweight value type inferred from a configuration value. */
public enum ValueType {
    STRING,
    INTEGER,
    BOOLEAN,
    DURATION,
    PLACEHOLDER,
    UNKNOWN
}
