package io.github.hzzzzzx.configradar.core.model;

/**
 * A single changed field for one key between two inventories.
 *
 * <p>{@code newSource} is the source path of the winning (highest Spring priority) finding in the
 * head inventory. Filtering logic uses this directly instead of reverse-looking-up a path from the
 * normalized key, so that real changes under profile-specific files are not dropped when a
 * lower-priority source happens to be unchanged. It may be {@code null} for changes that carry no
 * head source (e.g. deserialized from an older {@code changes.yaml}).
 */
public record ConfigChange(
    String key,
    String field,
    String oldValue,
    String newValue,
    String newSource
) {
    /** Backwards-compatible constructor; {@code newSource} defaults to {@code null}. */
    public ConfigChange(String key, String field, String oldValue, String newValue) {
        this(key, field, oldValue, newValue, null);
    }
}
