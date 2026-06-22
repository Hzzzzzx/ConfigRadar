package io.github.hzzzzzx.configradar.core.rule;

import java.util.List;

/** Project-local rule template loaded from config-radar-rules.yaml. */
public record ConfigRules(
    List<MethodCallRule> methodCalls,
    List<AnnotationRule> annotations,
    List<ConfigFileRule> configFiles
) {
    public ConfigRules {
        methodCalls = List.copyOf(methodCalls == null ? List.of() : methodCalls);
        annotations = List.copyOf(annotations == null ? List.of() : annotations);
        configFiles = List.copyOf(configFiles == null ? List.of() : configFiles);
    }

    public static ConfigRules empty() {
        return new ConfigRules(List.of(), List.of(), List.of());
    }
}
