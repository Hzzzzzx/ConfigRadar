package io.github.hzzzzzx.configradar.core.semantic;

import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.FindingDetails;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import java.util.Objects;

/** One configuration use discovered from an external code semantic index. */
public record CodeConfigUsage(
    String key,
    UsageKind kind,
    SourceLocation source,
    Confidence confidence,
    FindingDetails details
) {
    public CodeConfigUsage {
        Objects.requireNonNull(key, "key");
        kind = kind == null ? UsageKind.CUSTOM_ANNOTATION : kind;
        Objects.requireNonNull(source, "source");
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
    }
}
