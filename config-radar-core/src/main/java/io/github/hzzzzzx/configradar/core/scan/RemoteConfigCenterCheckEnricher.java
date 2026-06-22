package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigCenterDetails;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.InventoryCheck;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/** Adds review checks for remote config center references without fetching remote values. */
public final class RemoteConfigCenterCheckEnricher implements InventoryEnricher {
    private static final Set<String> REMOTE_IMPORT_PREFIXES = Set.of(
        "configserver:",
        "nacos:",
        "consul:",
        "zookeeper:",
        "etcd:",
        "apollo:"
    );

    @Override
    public String id() {
        return "remote-config-center-checks";
    }

    @Override
    public ConfigInventory enrich(ConfigInventory inventory, ScanContext context) {
        var checks = new ArrayList<>(inventory.checks());
        for (var item : inventory.items()) {
            if (isRemoteConfigReference(item)) {
                checks.add(new InventoryCheck(
                    "remote-config-source",
                    DiagnosticSeverity.WARNING,
                    "Remote config center reference needs external inventory review: " + item.key(),
                    item.key(),
                    item.source()
                ));
            }
        }
        if (checks.size() == inventory.checks().size()) {
            return inventory;
        }
        return new ConfigInventory(
            inventory.schemaVersion(),
            inventory.project(),
            inventory.summary(),
            inventory.items(),
            inventory.uncertain(),
            checks,
            inventory.diagnostics()
        );
    }

    private static boolean isRemoteConfigReference(ConfigFinding item) {
        if (item.details() instanceof ConfigCenterDetails) {
            return true;
        }
        var key = item.normalizedKey().toLowerCase(Locale.ROOT);
        if (key.startsWith("spring.cloud.config.") || key.startsWith("spring.cloud.nacos.config.")) {
            return true;
        }
        if (!key.equals("spring.config.import") || item.value() == null) {
            return false;
        }
        var value = item.value().raw().toLowerCase(Locale.ROOT);
        return REMOTE_IMPORT_PREFIXES.stream().anyMatch(prefix -> value.contains(prefix) || value.contains("optional:" + prefix));
    }
}
