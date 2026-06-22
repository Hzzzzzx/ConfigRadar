package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.InventorySummary;
import java.util.HashSet;

/** Recomputes summary after future enrichers add checks or diagnostics. */
public final class SummaryInventoryEnricher implements InventoryEnricher {
    @Override
    public String id() {
        return "summary";
    }

    @Override
    public ConfigInventory enrich(ConfigInventory inventory, ScanContext context) {
        var keys = new HashSet<String>();
        inventory.items().forEach(item -> keys.add(item.normalizedKey()));
        var summary = new InventorySummary(
            keys.size(),
            inventory.items().size(),
            inventory.uncertain().size(),
            inventory.checks().size(),
            inventory.diagnostics().size()
        );
        return inventory.withSummary(summary);
    }
}
