package io.github.hzzzzzx.configradar.core.model;

import java.util.Objects;

/** Dynamic or unresolved configuration access. */
public record UncertainFinding(
    String expression,
    UncertainReason reason,
    String rootSink,
    EnvironmentContext environment,
    SourceLocation source,
    Confidence confidence,
    String detectorId,
    UncertainDetails details
) implements ScanFinding {
    public UncertainFinding {
        Objects.requireNonNull(expression, "expression");
        reason = reason == null ? UncertainReason.UNKNOWN : reason;
        environment = environment == null ? EnvironmentContext.none() : environment;
        Objects.requireNonNull(source, "source");
        confidence = confidence == null ? Confidence.LOW : confidence;
        Objects.requireNonNull(detectorId, "detectorId");
        details = details == null ? new UnknownUncertainDetails(expression) : details;
    }
}
