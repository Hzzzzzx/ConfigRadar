package io.github.hzzzzzx.configradar.core.model;

/** Details for ${key} or ${key:default} placeholders. */
public record SpringPlaceholderDetails(
    String defaultValue,
    String rawExpression
) implements FindingDetails {
}
