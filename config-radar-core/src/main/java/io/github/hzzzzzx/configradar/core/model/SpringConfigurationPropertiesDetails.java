package io.github.hzzzzzx.configradar.core.model;

/** Details for keys inferred from @ConfigurationProperties. */
public record SpringConfigurationPropertiesDetails(
    String prefix,
    String boundType,
    boolean inferredFromFields
) implements FindingDetails {
}
