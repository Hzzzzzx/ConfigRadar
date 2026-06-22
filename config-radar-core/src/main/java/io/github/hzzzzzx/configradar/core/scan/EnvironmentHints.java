package io.github.hzzzzzx.configradar.core.scan;

/** Optional environment hints supplied by user or build metadata. */
public record EnvironmentHints(
    String activeProfile,
    String region,
    String namespace
) {
    public static EnvironmentHints none() {
        return new EnvironmentHints(null, null, null);
    }
}
