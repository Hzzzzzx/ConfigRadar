package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.InventoryCheck;
import java.util.ArrayList;

/** Adds high-risk checks for dynamic configuration access. */
public final class UncertainFindingCheckEnricher implements InventoryEnricher {
    @Override
    public String id() {
        return "uncertain-finding-checks";
    }

    @Override
    public ConfigInventory enrich(ConfigInventory inventory, ScanContext context) {
        if (inventory.uncertain().isEmpty()) {
            return inventory;
        }
        var checks = new ArrayList<>(inventory.checks());
        for (var uncertain : inventory.uncertain()) {
            checks.add(new InventoryCheck(
                "dynamic-config-key",
                DiagnosticSeverity.ERROR,
                "Dynamic configuration key requires review: " + uncertain.expression(),
                null,
                uncertain.source()
            ));
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
