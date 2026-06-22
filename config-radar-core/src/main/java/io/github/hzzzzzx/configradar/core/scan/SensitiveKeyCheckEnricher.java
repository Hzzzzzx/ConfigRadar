package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.InventoryCheck;
import java.util.ArrayList;

/** Adds review checks for keys whose names look sensitive. */
public final class SensitiveKeyCheckEnricher implements InventoryEnricher {
    private static final RedactionPolicy POLICY = RedactionPolicy.redactSensitive();

    @Override
    public String id() {
        return "sensitive-key-checks";
    }

    @Override
    public ConfigInventory enrich(ConfigInventory inventory, ScanContext context) {
        var checks = new ArrayList<>(inventory.checks());
        for (var item : inventory.items()) {
            if (POLICY.matchesKey(item.normalizedKey() == null ? item.key() : item.normalizedKey())) {
                checks.add(new InventoryCheck(
                    "sensitive-looking-key",
                    DiagnosticSeverity.WARNING,
                    "Configuration key name looks sensitive: " + item.key(),
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
}
