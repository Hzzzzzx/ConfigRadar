package io.github.hzzzzzx.configradar.core.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Typed details for unresolved dynamic configuration access. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = DynamicKeyDetails.class, name = "dynamic-key"),
    @JsonSubTypes.Type(value = UnknownUncertainDetails.class, name = "unknown")
})
public sealed interface UncertainDetails permits DynamicKeyDetails, UnknownUncertainDetails {
}
