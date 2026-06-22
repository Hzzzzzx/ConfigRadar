package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.model.Confidence;

/** Declarative rule for ConfigCenter.get("key") style calls. */
public record MethodCallRule(
    String id,
    String owner,
    String method,
    int keyArg,
    Integer defaultArg,
    Confidence confidence
) implements ConfigRule {
    public MethodCallRule {
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
    }
}
