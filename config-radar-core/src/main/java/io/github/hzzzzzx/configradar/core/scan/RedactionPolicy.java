package io.github.hzzzzzx.configradar.core.scan;

import java.util.List;

/** Output masking policy for sensitive configuration values. */
public record RedactionPolicy(
    boolean enabled,
    List<String> sensitiveKeyTokens,
    String replacement
) {
    public RedactionPolicy {
        sensitiveKeyTokens = List.copyOf(sensitiveKeyTokens == null || sensitiveKeyTokens.isEmpty()
            ? List.of("password", "passwd", "pwd", "secret", "token", "credential", "private-key")
            : sensitiveKeyTokens);
        replacement = replacement == null || replacement.isBlank() ? "******" : replacement;
    }

    public static RedactionPolicy disabled() {
        return new RedactionPolicy(false, List.of(), "******");
    }

    public static RedactionPolicy redactSensitive() {
        return new RedactionPolicy(true, List.of(), "******");
    }
}
