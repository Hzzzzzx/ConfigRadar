package io.github.hzzzzzx.configradar.core.model;

/** Details for config-center style sources or reads. */
public record ConfigCenterDetails(
    String namespace,
    String group,
    String dataId,
    String defaultValue
) implements FindingDetails {
}
