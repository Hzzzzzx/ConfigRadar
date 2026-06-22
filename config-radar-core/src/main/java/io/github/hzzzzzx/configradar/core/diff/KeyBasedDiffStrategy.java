package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigChange;
import io.github.hzzzzzx.configradar.core.model.ConfigCenterDetails;
import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.InventoryCheck;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Diffs inventories by normalized key, role, and profile. */
public final class KeyBasedDiffStrategy implements ConfigDiffStrategy {
    @Override
    public String id() {
        return "key";
    }

    @Override
    public ConfigDiff diff(ConfigInventory base, ConfigInventory head) {
        var baseItems = index(base.items());
        var headItems = index(head.items());
        var added = new ArrayList<ConfigFinding>();
        var removed = new ArrayList<ConfigFinding>();
        var changed = new ArrayList<ConfigChange>();
        var checks = new ArrayList<InventoryCheck>();

        for (var entry : headItems.entrySet()) {
            var before = baseItems.get(entry.getKey());
            if (before == null) {
                added.add(entry.getValue());
                addRemoteConfigCheck(checks, entry.getValue());
            } else {
                changed.addAll(changes(before, entry.getValue()));
            }
        }
        for (var entry : baseItems.entrySet()) {
            if (!headItems.containsKey(entry.getKey())) {
                removed.add(entry.getValue());
            }
        }

        var uncertainChanged = new ArrayList<UncertainFinding>();
        var baseUncertain = base.uncertain().stream().map(UncertainFinding::expression).toList();
        for (var item : head.uncertain()) {
            if (!baseUncertain.contains(item.expression())) {
                uncertainChanged.add(item);
                checks.add(new InventoryCheck(
                    "dynamic-config-key",
                    DiagnosticSeverity.ERROR,
                    "New dynamic configuration access requires review: "
                        + item.reason() + " via " + sink(item.rootSink()) + ": " + item.expression(),
                    null,
                    item.source()
                ));
            }
        }

        return new ConfigDiff(
            ConfigDiff.SCHEMA_VERSION,
            null,
            sorted(added),
            sorted(removed),
            changed.stream().sorted(Comparator.comparing(ConfigChange::key).thenComparing(ConfigChange::field)).toList(),
            uncertainChanged,
            checks
        );
    }

    private static LinkedHashMap<String, ConfigFinding> index(List<ConfigFinding> items) {
        var sorted = items.stream()
            .sorted(Comparator.comparing(KeyBasedDiffStrategy::identity))
            .toList();
        var result = new LinkedHashMap<String, ConfigFinding>();
        for (var item : sorted) {
            result.putIfAbsent(identity(item), item);
        }
        return result;
    }

    private static List<ConfigChange> changes(ConfigFinding before, ConfigFinding after) {
        var changes = new ArrayList<ConfigChange>();
        addChange(changes, after.normalizedKey(), "value", raw(before.value()), raw(after.value()));
        addChange(changes, after.normalizedKey(), "value.type", type(before.value()), type(after.value()));
        addChange(changes, after.normalizedKey(), "defaultValue", raw(before.defaultValue()), raw(after.defaultValue()));
        addChange(changes, after.normalizedKey(), "defaultValue.type", type(before.defaultValue()), type(after.defaultValue()));
        addChange(changes, after.normalizedKey(), "source.path", before.source().path(), after.source().path());
        return changes;
    }

    private static void addChange(List<ConfigChange> changes, String key, String field, String oldValue, String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new ConfigChange(key, field, oldValue, newValue));
        }
    }

    private static String identity(ConfigFinding item) {
        var environment = item.environment();
        var profile = environment == null ? "" : nullToEmpty(environment.profile());
        var region = environment == null ? "" : nullToEmpty(environment.region());
        var namespace = environment == null ? "" : nullToEmpty(environment.namespace());
        return item.normalizedKey() + "|" + item.role() + "|" + profile + "|" + region + "|" + namespace;
    }

    private static String raw(ConfigValue value) {
        return value == null ? null : value.raw();
    }

    private static String type(ConfigValue value) {
        return value == null || value.type() == null ? null : value.type().name();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sink(String rootSink) {
        return rootSink == null || rootSink.isBlank() ? "unknown sink" : rootSink;
    }

    private static void addRemoteConfigCheck(List<InventoryCheck> checks, ConfigFinding item) {
        if (!isRemoteConfigReference(item)) {
            return;
        }
        checks.add(new InventoryCheck(
            "remote-config-source",
            DiagnosticSeverity.WARNING,
            "New remote config center reference requires review: " + item.key(),
            item.key(),
            item.source()
        ));
    }

    private static boolean isRemoteConfigReference(ConfigFinding item) {
        if (item.details() instanceof ConfigCenterDetails) {
            return true;
        }
        var key = item.normalizedKey().toLowerCase(Locale.ROOT);
        return key.startsWith("spring.cloud.config.")
            || key.startsWith("spring.cloud.nacos.config.")
            || key.equals("spring.config.import");
    }

    private static List<ConfigFinding> sorted(List<ConfigFinding> findings) {
        return findings.stream().sorted(Comparator.comparing(KeyBasedDiffStrategy::identity)).toList();
    }
}
