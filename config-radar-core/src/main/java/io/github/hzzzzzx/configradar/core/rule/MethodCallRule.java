package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.FindingRole;

/** Declarative rule for ConfigCenter.get("key") style calls. */
public record MethodCallRule(
    String id,
    String owner,
    String method,
    int keyArg,
    Integer defaultArg,
    Confidence confidence,
    FindingRole role
) implements ConfigRule {
    public MethodCallRule {
        confidence = confidence == null ? Confidence.MEDIUM : confidence;
        role = role == null ? FindingRole.READ : role;
    }

    public MethodCallRule(String id, String owner, String method, int keyArg, Integer defaultArg, Confidence confidence) {
        this(id, owner, method, keyArg, defaultArg, confidence, FindingRole.READ);
    }
}
