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
 * {@link DiffConsumer} for the {@code xac} format: renders a config diff as three artifacts.
 *
 * <ol>
 *   <li>{@code app-configs-changed.yaml} — the keys to <b>add or update</b> that <b>have a value</b>:
 *       value-bearing {@code added} findings plus {@code changed} keys whose new value is non-empty.
 *       Partitioned into {@code app_configs} / {@code J2C.secrets} exactly like the full-inventory
 *       {@link XacConsumer}, so the same deploy tooling consumes both. This is the strict-shape
 *       artifact.</li>
 *   <li>{@code app-configs-missing.yaml} — valueless {@code added} findings and {@code changed} keys
 *       whose new value is empty. These need a human to fill in a value. Listed plainly, each with a
 *       {@code source} (where the key is referenced) so a reviewer knows where to look.</li>
 *   <li>{@code removed.yaml} — the keys to <b>delete</b> ({@code removed} findings), a plain list.</li>
 * </ol>
 *
 * <p>Entry building for the strict artifact is shared with {@link XacConsumer} via
 * {@link XacEntryBuilder}, so the two consumers never diverge on secret routing or grouping.
 *
 * <p>{@code changed} entries only carry key/field/oldValue/newValue/newSource (no full finding), so
 * their entries are built from the {@code value} field change; non-value field changes are skipped.
 */
public final class XacDiffConsumer implements DiffConsumer {

    @Override
    public String id() {
        return "xac";
    }

    @Override
    public void consume(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        writeChangedManifest(diff, context, sink);
        writeMissingList(diff, context, sink);
        writeRemovedList(diff, context, sink);
    }

    /** File 1: value-bearing added/changed keys, strict XAC shape (app_configs / J2C.secrets). */
    private void writeChangedManifest(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        var entries = new ArrayList<AppConfigEntry>();
        var secrets = new ArrayList<J2cSecretEntry>();

        // added: only value-bearing findings go into the strict manifest.
        for (var finding : diff.added()) {
            var value = XacEntryBuilder.valueOr(finding);
            if (value.isBlank()) {
                continue; // valueless added keys go to the missing list instead
            }
            var key = finding.normalizedKey();
            if (XacEntryBuilder.SENSITIVE.matchesKey(key)) {
                secrets.add(XacEntryBuilder.toSecret(key, value, finding));
            } else {
                entries.add(XacEntryBuilder.toEntry(key, value));
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
            var value = change.newValue();
            if (value == null || value.isBlank()) {
                continue; // valueless changes go to the missing list instead
            }
            if (XacEntryBuilder.SENSITIVE.matchesKey(key)) {
                secrets.add(XacEntryBuilder.toSecret(key, value, null));
            } else {
                entries.add(XacEntryBuilder.toEntry(key, value));
            }
        }

        // Nothing value-bearing to upsert -> skip the file entirely rather than emit an empty manifest.
        if (entries.isEmpty() && secrets.isEmpty()) {
            return;
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
        manifest.put("metadata", Map.of("name", appName(context)));
        manifest.put("data", data);

        try (var out = sink.openFile("app-configs-changed.yaml");
             var writer = new OutputStreamWriter(out)) {
            ManifestYaml.dump(manifest, writer);
        }
    }

    /** File 2: valueless added/changed keys, plain list with source reference for review. */
    private void writeMissingList(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        var items = new ArrayList<Map<String, Object>>();

        for (var finding : diff.added()) {
            if (!XacEntryBuilder.valueOr(finding).isBlank()) {
                continue;
            }
            items.add(missingItem(finding.normalizedKey(), finding.source().path(), "added without value"));
        }

        var seen = new java.util.HashSet<String>();
        for (var change : diff.changed()) {
            if (!change.field().equals("value")) {
                continue;
            }
            var key = change.key();
            if (!seen.add(key)) {
                continue;
            }
            var value = change.newValue();
            if (value != null && !value.isBlank()) {
                continue;
            }
            items.add(missingItem(key, change.newSource(), "changed to empty value"));
        }

        if (items.isEmpty()) {
            return;
        }

        var root = new LinkedHashMap<String, Object>();
        root.put("apiVersion", "com.huawei.his.appconfigcenter.v3");
        root.put("kind", "his.appconfigcenter");
        root.put("metadata", Map.of("name", appName(context)));
        root.put("missing", items);

        try (var out = sink.openFile("app-configs-missing.yaml");
             var writer = new OutputStreamWriter(out)) {
            ManifestYaml.dump(root, writer);
        }
    }

    private static Map<String, Object> missingItem(String key, String source, String reason) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("config_key", key);
        entry.put("source", source);
        entry.put("reason", reason);
        return entry;
    }

    /** File 3: removed keys, plain list. */
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

        if (items.isEmpty()) {
            return;
        }

        var root = new LinkedHashMap<String, Object>();
        root.put("apiVersion", "com.huawei.his.appconfigcenter.v3");
        root.put("kind", "his.appconfigcenter");
        root.put("metadata", Map.of("name", appName(context)));
        root.put("removed", items);

        try (var out = sink.openFile("removed.yaml");
             var writer = new OutputStreamWriter(out)) {
            ManifestYaml.dump(root, writer);
        }
    }

    private static String appName(ConsumerContext context) {
        var name = context.property("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return "app";
    }
}
