package io.github.hzzzzzx.configradar.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** Controlled escape hatch for third-party packs and experimental detectors. */
public record ExternalDetails(
    String namespace,
    String type,
    JsonNode payload
) implements FindingDetails {
    public ExternalDetails {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(type, "type");
    }
}
