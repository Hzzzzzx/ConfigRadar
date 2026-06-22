package io.github.hzzzzzx.configradar.core.model;

/** Timing for one pipeline phase in milliseconds. */
public record PerformanceMetric(
    String phase,
    long millis
) {
}
