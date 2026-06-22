package io.github.hzzzzzx.configradar.core.model;

/** Details for System property/env access. */
public record JavaSystemPropertyDetails(
    String defaultValue,
    boolean fromConstant
) implements FindingDetails {
}
