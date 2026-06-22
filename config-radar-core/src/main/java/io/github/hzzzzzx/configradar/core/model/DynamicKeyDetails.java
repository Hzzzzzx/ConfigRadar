package io.github.hzzzzzx.configradar.core.model;

/** Known fragments from a dynamic key expression. */
public record DynamicKeyDetails(
    String knownPrefix,
    String knownSuffix,
    String rawExpression
) implements UncertainDetails {
}
