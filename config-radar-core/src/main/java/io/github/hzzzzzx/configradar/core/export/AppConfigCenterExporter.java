package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a ConfigRadar {@link ConfigInventory} into the application-config-center
 * {@code app_configs} format.
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
 * ConfigRadar does not record the full 17-layer PropertySource order. The output flags this with
 * {@code priority: approximate}.
 */
public final class AppConfigCenterExporter {

    /** Placeholder scope used for every entry; deploy-time metadata ConfigRadar cannot know. */
    public static final String DEFAULT_SCOPE = "${app_deploy_unit_name}";

    /** Default encrypt type used by the J2C secret template. */
    public static final String DEFAULT_ENCRYPT_TYPE = "ADVANCED2.6";

    /** Default init source for J2C secrets (manual input). */
    public static final String DEFAULT_INIT_SOURCE = "input";

    /** Default version for newly discovered config entries. */
    public static final String DEFAULT_VERSION = "1.0";

    private static final RedactionPolicy SENSITIVE = RedactionPolicy.redactSensitive();

    /**
     * Output mode.
     * <ul>
     *   <li>{@code DEFAULT} — plain config inventory; sensitive keys stay in {@code app_configs}
     *       flagged with {@code secret: 1}. No J2C section.</li>
     *   <li>{@code XAC} — platform artifact for the internal XAC deployment platform; sensitive
     *       keys are routed to a separate {@code J2C.secrets} section with placeholder passwords,
     *       and {@code app_configs} holds only non-sensitive keys.</li>
     * </ul>
     */
    public enum ExportFormat {
        DEFAULT,
        XAC
    }

    /** Result of an export: the main list, J2C secrets, and (optionally) the missing-default list. */
    public record ExportResult(
        List<AppConfigEntry> entries,
        List<J2cSecretEntry> secrets,
        List<AppConfigEntry> missing
    ) {
    }

    /**
     * Exports an inventory in {@link ExportFormat#XAC} (the default, partitioned output).
     *
     * @param inventory the ConfigRadar inventory to convert
     * @return main entries, J2C secrets, and missing-default entries
     */
    public ExportResult export(ConfigInventory inventory) {
        return export(inventory, ExportFormat.XAC);
    }

    /**
     * Exports an inventory in the chosen format.
     *
     * @param inventory the ConfigRadar inventory to convert
     * @param format    output mode (DEFAULT keeps sensitive keys in app_configs; XAC routes them to J2C)
     * @return main entries, J2C secrets, and missing-default entries
     */
    public ExportResult export(ConfigInventory inventory, ExportFormat format) {
        var byKey = deduplicate(inventory.items());
        var entries = new ArrayList<AppConfigEntry>();
        var secrets = new ArrayList<J2cSecretEntry>();
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
            boolean sensitive = SENSITIVE.matchesKey(key);
            if (sensitive && format == ExportFormat.XAC) {
                // Sensitive keys route to the J2C secrets section in XAC mode.
                secrets.add(toSecret(key, hasValue ? valueOr(winner) : null, winner));
            } else if (hasValue) {
                entries.add(toEntry(key, valueOr(winner), winner));
            } else {
                // Read but never defined and no default — needs a value filled in.
                missing.add(toEntry(key, "", winner));
            }
        }

        entries.sort(Comparator.comparing(AppConfigEntry::config_key));
        secrets.sort(Comparator.comparing(J2cSecretEntry::key));
        missing.sort(Comparator.comparing(AppConfigEntry::config_key));
        return new ExportResult(entries, secrets, missing);
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
     */
    static int springPriority(ConfigFinding finding) {
        var source = finding.source();
        var path = source.path() == null ? "" : source.path().toLowerCase(Locale.ROOT);
        var profile = finding.environment() == null ? null : finding.environment().profile();
        var fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

        // .env / SPRING_APPLICATION_JSON-style environment overrides (highest).
        if (fileName.startsWith(".env")) {
            return 90;
        }
        // profile-specific application-{profile}.yml/properties.
        if (profile != null && !profile.isBlank()
            && (fileName.startsWith("application-") || fileName.startsWith("bootstrap-"))) {
            return 80;
        }
        // default application.yml / application.properties (profile-less base).
        if (fileName.startsWith("application.yml")
            || fileName.startsWith("application.yaml")
            || fileName.startsWith("application.properties")) {
            return 70;
        }
        // bootstrap files (lower priority than application).
        if (fileName.startsWith("bootstrap.")) {
            return 50;
        }
        // programmatic setProperty in Java source.
        if (source.sourceKind() == SourceKind.JAVA) {
            return 30;
        }
        return 10;
    }

    private AppConfigEntry toEntry(String normalizedKey, String value, ConfigFinding finding) {
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

    /**
     * Builds a J2C secret entry for a sensitive key. The {@code key} is the underscore form of the
     * config key (e.g. {@code db.password} -> {@code db_password}); {@code password} is a
     * placeholder built from that underscore key, since the real secret is provisioned elsewhere.
     */
    private J2cSecretEntry toSecret(String normalizedKey, String value, ConfigFinding finding) {
        var underscoreKey = toUnderscore(normalizedKey);
        var type = typeHint(normalizedKey);
        return new J2cSecretEntry(
            underscoreKey,
            DEFAULT_INIT_SOURCE,
            type == null ? "" : type,
            "",
            "${" + underscoreKey + "}",
            DEFAULT_ENCRYPT_TYPE,
            remarkOf(normalizedKey),
            DEFAULT_SCOPE
        );
    }

    /** Converts a normalized dotted/hyphenated key into an underscore form for placeholder names. */
    static String toUnderscore(String key) {
        if (key == null || key.isBlank()) {
            return "config";
        }
        return key.replace('-', '_').replace('.', '_').toLowerCase(Locale.ROOT);
    }

    /** Best-effort connection type hint from the key (e.g. {@code db.password} -> {@code mysql}). */
    private static String typeHint(String key) {
        var lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("redis")) {
            return "redis";
        }
        if (lower.startsWith("db.") || lower.contains("datasource") || lower.contains("jdbc")) {
            return "mysql";
        }
        return null;
    }

    private static String remarkOf(String key) {
        var type = typeHint(key);
        return type == null ? "" : type;
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
