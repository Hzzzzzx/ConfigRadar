package io.github.hzzzzzx.configradar.core.model;

/** Environment dimensions used for grouping and diff identity. */
public record EnvironmentContext(
    String profile,
    String region,
    String namespace
) {
    public static EnvironmentContext none() {
        return new EnvironmentContext(null, null, null);
    }
}
