package io.github.hzzzzzx.configradar.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Typed details for stable built-in detectors. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SpringPlaceholderDetails.class, name = "spring-placeholder"),
    @JsonSubTypes.Type(value = SpringConfigurationPropertiesDetails.class, name = "spring-configuration-properties"),
    @JsonSubTypes.Type(value = JavaSystemPropertyDetails.class, name = "java-system-property"),
    @JsonSubTypes.Type(value = ConfigCenterDetails.class, name = "config-center"),
    @JsonSubTypes.Type(value = ExternalDetails.class, name = "external"),
    @JsonSubTypes.Type(value = UnknownDetails.class, name = "unknown")
})
public sealed interface FindingDetails permits
    SpringPlaceholderDetails,
    SpringConfigurationPropertiesDetails,
    JavaSystemPropertyDetails,
    ConfigCenterDetails,
    ExternalDetails,
    UnknownDetails {
}
