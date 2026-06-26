package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.model.ConfigChange;
import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.output.DiffConsumer;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link DiffConsumer} for the {@code xac} format: renders a config diff as two XAC-platform
 * artifacts.
 *
 * <ul>
 *   <li>{@code app-configs-changed.yaml} — a manifest of the keys to <b>add or update</b> in the
 *       config center: all {@code added} findings plus the new values of {@code changed} keys.
 *       Partitioned into {@code app_configs} / {@code J2C.secrets} exactly like the full-inventory
 *       {@link XacConsumer}, so the same deploy tooling consumes both.</li>
 *   <li>{@code removed.yaml} — a plain list of the keys to <b>delete</b> from the config center
 *       ({@code removed} findings), as requested for the diff scenario.</li>
 * </ul>
 *
 * <p>Entry building is shared with {@link XacConsumer} via {@link XacEntryBuilder}, so the two
 * consumers never disagree on secret routing, group derivation, etc.
 *
 * <p>{@code changed} entries only carry key/field/oldValue/newValue/newSource (no full finding),
 * so their entries are built from the {@code value} field change. Keys whose only change is a
 * non-value field (e.g. {@code value.type}, {@code source.path}) are skipped, since there is no new
 * deployable value to publish.
 */
public final class XacDiffConsumer implements DiffConsumer {

    @Override
    public String id() {
        return "xac";
    }

    @Override
    public void consume(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        writeUpsertManifest(diff, context, sink);
        writeRemovedList(diff, context, sink);
    }

    private void writeUpsertManifest(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        var entries = new ArrayList<AppConfigEntry>();
        var secrets = new ArrayList<J2cSecretEntry>();

        // added: full findings, value-bearing.
        for (var finding : diff.added()) {
            var key = finding.normalizedKey();
            if (XacEntryBuilder.SENSITIVE.matchesKey(key)) {
                secrets.add(XacEntryBuilder.toSecret(key, XacEntryBuilder.valueOr(finding), finding));
            } else {
                entries.add(XacEntryBuilder.toEntry(key, XacEntryBuilder.valueOr(finding)));
            }
        }

        // changed: only the "value" field change carries a publishable new value; dedupe by key
        // (a key may have multiple field changes; only the value one matters here).
        var seen = new java.util.HashSet<String>();
        for (var change : diff.changed()) {
            if (!change.field().equals("value")) {
                continue;
            }
            var key = change.key();
            if (!seen.add(key)) {
                continue;
            }
            var value = change.newValue() != null ? change.newValue() : "";
            if (XacEntryBuilder.SENSITIVE.matchesKey(key)) {
                secrets.add(XacEntryBuilder.toSecret(key, value, null));
            } else {
                entries.add(XacEntryBuilder.toEntry(key, value));
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
        manifest.put("metadata", Map.of("name", appName(context, diff)));
        manifest.put("data", data);

        try (var out = sink.openFile("app-configs-changed.yaml");
             var writer = new OutputStreamWriter(out)) {
            ManifestYaml.dump(manifest, writer);
        }
    }

    private void writeRemovedList(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        var items = diff.removed().stream()
            .map(ConfigFinding::normalizedKey)
            .distinct()
            .sorted()
            .map(key -> {
                var entry = new LinkedHashMap<String, Object>();
                entry.put("config_key", key);
                entry.put("group_name", XacEntryBuilder.groupOf(key));
                return entry;
            })
            .toList();

        var root = new LinkedHashMap<String, Object>();
        root.put("apiVersion", "com.huawei.his.appconfigcenter.v3");
        root.put("kind", "his.appconfigcenter");
        root.put("metadata", Map.of("name", appName(context, diff)));
        root.put("removed", items);

        try (var out = sink.openFile("removed.yaml");
             var writer = new OutputStreamWriter(out)) {
            ManifestYaml.dump(root, writer);
        }
    }

    private static String appName(ConsumerContext context, ConfigDiff diff) {
        var name = context.property("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "app";
    }
}
