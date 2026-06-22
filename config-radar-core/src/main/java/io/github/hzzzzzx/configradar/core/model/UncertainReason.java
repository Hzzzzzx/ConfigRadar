package io.github.hzzzzzx.configradar.core.model;

/** Why a finding cannot be promoted to a confirmed key. */
public enum UncertainReason {
    STRING_CONCAT,
    VARIABLE_KEY,
    METHOD_RETURN_KEY,
    MAP_DRIVEN_KEY,
    ENUM_DRIVEN_KEY,
    TENANT_OR_REGION_KEY,
    UNKNOWN_WRAPPER,
    REMOTE_CONFIG_ACCESS,
    UNKNOWN
}
