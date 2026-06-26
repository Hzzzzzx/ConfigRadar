package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import io.github.hzzzzzx.configradar.core.scan.SpringPriority;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Converts a ConfigRadar {@link ConfigInventory} into the {@code app_configs} format.
 *
 * <p>The conversion does three things:
 * <ol>
 *   <li><b>Deduplicate by Spring priority</b> — the same key may be defined in several files
 *       (e.g. {@code application.yml} and {@code application-prod.yml}); keep the one from the
 *       highest-priority source, approximating Spring Boot's externalized-configuration order.</li>
 *   <li><b>Map fields</b> — key, value, group (first key segment), and a sensitive flag.</li>
 *   <li><b>Separate missing defaults</b> — keys read in code but never defined and without a
 *       default are reported separately so they can be filled in and merged back.</li>
 * </ol>
 *
 * <p>The Spring priority is an <em>approximation</em> inferred from file name and profile, since
 * ConfigRadar does not record the full 17-layer PropertySource order.
 */
public final class AppConfigCenterExporter {

    /** Placeholder scope used for every entry; deploy-time metadata ConfigRadar cannot know. */
    public static final String DEFAULT_SCOPE = "${app_deploy_unit_name}";

    /** Default version for newly discovered config entries. */
    public static final String DEFAULT_VERSION = "1.0";

    private static final RedactionPolicy SENSITIVE = RedactionPolicy.redactSensitive();

    /** Result of an export: the main list and (optionally) the missing-default list. */
    public record ExportResult(
        List<AppConfigEntry> entries,
        List<AppConfigEntry> missing
    ) {
    }

    /**
     * Exports an inventory to the plain app_configs format.
     *
     * <p>Sensitive keys stay in {@code app_configs} flagged with {@code secret: 1}.
     *
     * @param inventory the ConfigRadar inventory to convert
     * @return main entries and missing-default entries
     */
    public ExportResult export(ConfigInventory inventory) {
        var byKey = deduplicate(inventory.items());
        var entries = new ArrayList<AppConfigEntry>();
        var missing = new ArrayList<AppConfigEntry>();
        var definedKeys = new java.util.HashSet<String>();

        // First pass: keys that have at least one DEFINE finding are "defined".
        for (var item : inventory.items()) {
            if (item.role() == FindingRole.DEFINE) {
                definedKeys.add(item.normalizedKey());
            }
        }

        for (var entry : byKey.entrySet()) {
            var key = entry.getKey();
            var winner = entry.getValue();
            boolean hasValue = definedKeys.contains(key) || winner.defaultValue() != null;
            if (hasValue) {
                entries.add(toEntry(key, valueOr(winner)));
            } else {
                // Read but never defined and no default — needs a value filled in.
                missing.add(toEntry(key, ""));
            }
        }

        entries.sort(Comparator.comparing(AppConfigEntry::config_key));
        missing.sort(Comparator.comparing(AppConfigEntry::config_key));
        return new ExportResult(entries, missing);
    }

    /**
     * Merges filled-in missing entries back into the main export. Missing entries (by config_key)
     * override the main list's value — they are the human/skill-supplied values.
     *
     * @param base the inventory-derived main entries
     * @param filled the user-filled missing entries
     * @return merged list sorted by config_key
     */
    public List<AppConfigEntry> merge(List<AppConfigEntry> base, List<AppConfigEntry> filled) {
        var byKey = new LinkedHashMap<String, AppConfigEntry>();
        for (var entry : base) {
            byKey.put(entry.config_key(), entry);
        }
        for (var entry : filled) {
            byKey.put(entry.config_key(), entry);
        }
        var merged = new ArrayList<>(byKey.values());
        merged.sort(Comparator.comparing(AppConfigEntry::config_key));
        return merged;
    }

    private String valueOr(ConfigFinding finding) {
        if (finding.value() != null) {
            return finding.value().raw();
        }
        if (finding.defaultValue() != null) {
            return finding.defaultValue().raw();
        }
        return "";
    }

    /** Deduplicates findings by normalizedKey, keeping the highest Spring-priority source. */
    private LinkedHashMap<String, ConfigFinding> deduplicate(List<ConfigFinding> items) {
        var sorted = items.stream()
            .sorted(Comparator
                .comparingInt(AppConfigCenterExporter::springPriority).reversed()
                .thenComparing(ConfigFinding::normalizedKey))
            .toList();
        var result = new LinkedHashMap<String, ConfigFinding>();
        for (var item : sorted) {
            result.putIfAbsent(item.normalizedKey(), item);
        }
        return result;
    }

    /**
     * Approximates Spring Boot externalized-configuration priority from source evidence.
     * Higher number = higher priority (wins). This is a heuristic, not the full Spring order.
     * Public so downstream consumers (e.g. the XAC module) can reuse the same ranking.
     *
     * @deprecated delegates to {@link io.github.hzzzzzx.configradar.core.scan.SpringPriority#of};
     * prefer the relocated utility directly.
     */
    @Deprecated
    public static int springPriority(ConfigFinding finding) {
        return SpringPriority.of(finding);
    }

    private AppConfigEntry toEntry(String normalizedKey, String value) {
        return new AppConfigEntry(
            DEFAULT_SCOPE,
            groupOf(normalizedKey),
            normalizedKey,
            value,
            SENSITIVE.matchesKey(normalizedKey) ? 1 : 0,
            "",
            DEFAULT_VERSION,
            DEFAULT_VERSION,
            ""
        );
    }

    /** First segment of a dotted key, used as the config-center group. {@code db.host} -> {@code db}. */
    private static String groupOf(String key) {
        if (key == null || key.isBlank()) {
            return "default";
        }
        var dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : "default";
    }
}
