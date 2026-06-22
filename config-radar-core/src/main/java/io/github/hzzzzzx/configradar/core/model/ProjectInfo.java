package io.github.hzzzzzx.configradar.core.model;

/** Project metadata attached to inventory output. */
public record ProjectInfo(
    String name,
    String ref
) {
    public static ProjectInfo unknown() {
        return new ProjectInfo("unknown", "unknown");
    }
}
