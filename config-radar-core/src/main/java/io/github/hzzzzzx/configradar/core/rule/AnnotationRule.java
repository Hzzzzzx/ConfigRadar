package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.model.Confidence;

/** Declarative rule for custom @ConfigValue-like annotations. */
public record AnnotationRule(
    String id,
    String type,
    String keyAttribute,
    String defaultAttribute,
    Confidence confidence
) implements ConfigRule {
    public AnnotationRule {
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
    }
}
