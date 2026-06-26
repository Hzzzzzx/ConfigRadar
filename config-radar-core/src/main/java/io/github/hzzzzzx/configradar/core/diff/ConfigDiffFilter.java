package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Filters a {@link ConfigDiff} to only the changes that involve files in a given set.
 *
 * <p>This is used by the {@code config-diff} command to reduce noise: instead of reporting every
 * key that differs across two inventories, only keys whose source file is among the git-changed
 * files are kept. The filter preserves the diff schema; summary counts are recomputed.
 *
 * <p>Filtering rules per section:
 * <ul>
 *   <li>{@code added} / {@code removed}: {@link ConfigFinding} carries {@code source().path()} directly.</li>
 *   <li>{@code uncertainChanged} / {@code checks}: each entry carries its own source path.</li>
 *   <li>{@code changed} ({@code ConfigChange} has no source path): resolved via a
 *       {@code normalizedKey -> path} index built from the head inventory.</li>
 * </ul>
 */
public final class ConfigDiffFilter {

    /**
     * Returns a new diff containing only changes whose source file is in {@code changedFiles}.
     *
     * @param diff the full diff to filter
     * @param changedFiles file paths relative to the repository root (forward slashes)
     * @param head the head inventory (reserved for future use; {@code changed} entries now carry
     *             their own {@code newSource}, so source paths no longer need to be resolved by key)
     * @return a filtered diff (sections may be empty; summary is recomputed)
     */
    public ConfigDiff filter(ConfigDiff diff, Set<String> changedFiles, ConfigInventory head) {
        var normalized = normalize(changedFiles);

        var added = diff.added().stream()
            .filter(f -> normalized.contains(normalizePath(f.source().path())))
            .toList();
        var removed = diff.removed().stream()
            .filter(f -> normalized.contains(normalizePath(f.source().path())))
            .toList();
        // changed entries carry their own newSource (the winning finding's path), so we filter
        // directly on it instead of reverse-looking-up a path from the normalized key. This avoids
        // dropping real changes when a lower-priority source for the same key happens to be unchanged.
        var changed = diff.changed().stream()
            .filter(c -> c.newSource() != null && normalized.contains(normalizePath(c.newSource())))
            .toList();
        var uncertainChanged = diff.uncertainChanged().stream()
            .filter(u -> normalized.contains(normalizePath(u.source().path())))
            .toList();
        var checks = diff.checks().stream()
            .filter(ch -> ch.source() != null && normalized.contains(normalizePath(ch.source().path())))
            .toList();

        return new ConfigDiff(
            diff.schemaVersion(),
            null, // recomputed by the ConfigDiff constructor
            added,
            removed,
            changed,
            uncertainChanged,
            checks
        );
    }

    private static Set<String> normalize(Set<String> files) {
        var out = new HashSet<String>();
        for (var f : files) {
            out.add(normalizePath(f));
        }
        return out;
    }

    private static String normalizePath(String path) {
        return path == null ? "" : path.replace('\\', '/').toLowerCase(Locale.ROOT);
    }
}
