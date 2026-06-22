package io.github.hzzzzzx.configradar.core.model;

import java.util.Objects;

/** Confirmed configuration fact. Key and normalizedKey are required. */
public record ConfigFinding(
    String key,
    String normalizedKey,
    FindingRole role,
    ConfigValue value,
    ConfigValue defaultValue,
    EnvironmentContext environment,
    SourceLocation source,
    Confidence confidence,
    String detectorId,
    FindingDetails details
) implements ScanFinding {
    public ConfigFinding {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(normalizedKey, "normalizedKey");
        role = role == null ? FindingRole.READ : role;
        environment = environment == null ? EnvironmentContext.none() : environment;
        Objects.requireNonNull(source, "source");
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
        Objects.requireNonNull(detectorId, "detectorId");
        details = details == null ? new UnknownDetails("missing-details", "") : details;
    }
}
