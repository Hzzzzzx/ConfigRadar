package io.github.hzzzzzx.configradar.core.rule;

/** Marker interface for rule records supported by ConfigRadar. */
public sealed interface ConfigRule permits MethodCallRule, AnnotationRule, ConfigFileRule {
    String id();
}
