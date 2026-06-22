package io.github.hzzzzzx.configradar.core.model;

/** Counts in diff output. */
public record DiffSummary(
    int added,
    int removed,
    int changed,
    int uncertainChanged
) {
}
