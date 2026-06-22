package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.FindingRole;

/** Declarative rule for custom @ConfigValue-like annotations. */
public record AnnotationRule(
    String id,
    String type,
    String keyAttribute,
    String defaultAttribute,
    Confidence confidence,
    FindingRole role,
    String valueAttribute
) implements ConfigRule {
    public AnnotationRule {
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
        role = role == null ? FindingRole.READ : role;
    }

    public AnnotationRule(String id, String type, String keyAttribute, String defaultAttribute, Confidence confidence) {
        this(id, type, keyAttribute, defaultAttribute, confidence, FindingRole.READ, null);
    }

    public AnnotationRule(
        String id,
        String type,
        String keyAttribute,
        String defaultAttribute,
        Confidence confidence,
        FindingRole role
    ) {
        this(id, type, keyAttribute, defaultAttribute, confidence, role, null);
    }
}
