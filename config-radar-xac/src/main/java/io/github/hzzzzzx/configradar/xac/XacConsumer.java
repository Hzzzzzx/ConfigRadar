package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.export.AppConfigCenterExporter;
import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.output.InventoryConsumer;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reference {@link InventoryConsumer} for the {@code xac} format: an artifact for the internal XAC
 * deployment platform.
 *
 * <p>The output follows the platform's manifest shape (issue #3): fixed {@code apiVersion}/{@code kind}
 * headers, an application {@code metadata.name}, and all config under {@code data}. It is
 * partitioned: non-sensitive keys go to {@code data.app_configs}, sensitive keys
 * (password/secret/token/credential) go to {@code data.J2C.secrets} with placeholder passwords
 * derived from the key (underscore form), since the real secret is provisioned out-of-band.
 *
 * <p>This consumer lives in its own module ({@code config-radar-xac}) so downstream teams can
 * evolve the XAC format without touching ConfigRadar core. It is discovered at runtime via
 * {@link java.util.ServiceLoader} (registered in {@code META-INF/services}).
 */
public final class XacConsumer implements InventoryConsumer {

    private static final RedactionPolicy SENSITIVE = RedactionPolicy.redactSensitive();

    /** Placeholder scope used for every entry; deploy-time metadata ConfigRadar cannot know. */
    private static final String DEFAULT_SCOPE = "${app_deploy_unit_name}";

    /** Default encrypt type used by the J2C secret template. */
    private static final String DEFAULT_ENCRYPT_TYPE = "ADVANCED2.6";

    /** Default init source for J2C secrets (manual input). */
    private static final String DEFAULT_INIT_SOURCE = "input";

    @Override
    public String id() {
        return "xac";
    }

    @Override
    public void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception {
        var byKey = deduplicate(inventory.items());
        var entries = new ArrayList<AppConfigEntry>();
        var secrets = new ArrayList<J2cSecretEntry>();
        var definedKeys = new java.util.HashSet<String>();
        for (var item : inventory.items()) {
            if (item.role() == FindingRole.DEFINE) {
                definedKeys.add(item.normalizedKey());
            }
        }

        for (var entry : byKey.entrySet()) {
            var key = entry.getKey();
            var winner = entry.getValue();
            boolean hasValue = definedKeys.contains(key) || winner.defaultValue() != null;
            if (SENSITIVE.matchesKey(key)) {
                secrets.add(toSecret(key, hasValue ? valueOr(winner) : "", winner));
            } else if (hasValue) {
                entries.add(toEntry(key, valueOr(winner)));
            }
        }

        entries.sort(Comparator.comparing(AppConfigEntry::config_key));
        secrets.sort(Comparator.comparing(J2cSecretEntry::key));

        var data = new LinkedHashMap<String, Object>();
        data.put("app_configs", entries);
        if (!secrets.isEmpty()) {
            data.put("J2C", Map.of("secrets", secrets));
        }

        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("apiVersion", "com.huawei.his.appconfigcenter.v3");
        manifest.put("kind", "his.appconfigcenter");
        var appName = context.property("name");
        if (appName == null || appName.isBlank()) {
            var project = inventory.project();
            appName = (project != null && !"unknown".equals(project.name())) ? project.name() : "app";
        }
        manifest.put("metadata", Map.of("name", appName));
        manifest.put("data", data);

        try (var out = sink.openFile("app-configs.yaml");
             var writer = new OutputStreamWriter(out)) {
            ManifestYaml.dump(manifest, writer);
        }
    }

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

    private String valueOr(ConfigFinding finding) {
        if (finding.value() != null) {
            return finding.value().raw();
        }
        if (finding.defaultValue() != null) {
            return finding.defaultValue().raw();
        }
        return "";
    }

    private AppConfigEntry toEntry(String normalizedKey, String value) {
        return new AppConfigEntry(
            DEFAULT_SCOPE,
            groupOf(normalizedKey),
            normalizedKey,
            value,
            SENSITIVE.matchesKey(normalizedKey) ? 1 : 0,
            "",
            AppConfigCenterExporter.DEFAULT_VERSION,
            AppConfigCenterExporter.DEFAULT_VERSION,
            ""
        );
    }

    private J2cSecretEntry toSecret(String normalizedKey, String value, ConfigFinding finding) {
        var underscoreKey = toUnderscore(normalizedKey);
        var type = typeHint(normalizedKey);
        return new J2cSecretEntry(
            underscoreKey,
            DEFAULT_INIT_SOURCE,
            type,
            "",
            "${" + underscoreKey + "}",
            DEFAULT_ENCRYPT_TYPE,
            type.isEmpty() ? "" : type,
            DEFAULT_SCOPE
        );
    }

    private static String toUnderscore(String key) {
        if (key == null || key.isBlank()) {
            return "config";
        }
        return key.replace('-', '_').replace('.', '_').toLowerCase(Locale.ROOT);
    }

    private static String typeHint(String key) {
        var lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("redis")) {
            return "redis";
        }
        if (lower.startsWith("db.") || lower.contains("datasource") || lower.contains("jdbc")) {
            return "mysql";
        }
        return "";
    }

    private static String groupOf(String key) {
        if (key == null || key.isBlank()) {
            return "default";
        }
        var dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : "default";
    }
}
