package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;

/** Ordered component for summaries, checks, risk tags, and diagnostics. */
public interface InventoryEnricher {
    /**
     * Stable enricher id used for diagnostics and future plugin reporting.
     *
     * @return unique enricher id
     */
    String id();

    /**
     * Adds derived summaries, checks, risk labels, or diagnostics to an inventory.
     *
     * @param inventory inventory produced by the builder or previous enrichers
     * @param context scan context
     * @return enriched inventory
     */
    ConfigInventory enrich(ConfigInventory inventory, ScanContext context);
}
