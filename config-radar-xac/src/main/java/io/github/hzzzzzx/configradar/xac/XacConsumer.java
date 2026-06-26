package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.export.AppConfigCenterExporter;
import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.output.InventoryConsumer;
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

    @Override
    public String id() {
        return "xac";
    }

    @Override
    public void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception {
        var byKey = deduplicate(inventory.items());
        var scopeMapping = XacEntryBuilder.buildScopeMapping(context);
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
            var scope = scopeMapping.resolve(XacEntryBuilder.profileOf(winner));
            boolean hasValue = definedKeys.contains(key) || winner.defaultValue() != null;
            if (XacEntryBuilder.SENSITIVE.matchesKey(key)) {
                secrets.add(XacEntryBuilder.toSecret(key, hasValue ? XacEntryBuilder.valueOr(winner) : "", winner, scope));
            } else if (hasValue) {
                entries.add(XacEntryBuilder.toEntry(key, XacEntryBuilder.valueOr(winner), scope));
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
}
