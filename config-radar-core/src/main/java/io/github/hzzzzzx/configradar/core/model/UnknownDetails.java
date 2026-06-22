package io.github.hzzzzzx.configradar.core.model;

/** Quarantine for raw facts that are not stable enough to model yet. */
public record UnknownDetails(
    String reason,
    String rawText
) implements FindingDetails {
}
