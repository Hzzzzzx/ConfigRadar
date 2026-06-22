package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import java.util.List;

/** Builds the canonical inventory from normalized findings. */
public interface InventoryBuilder {
    /**
     * Converts normalized pipeline findings into the canonical inventory shape.
     *
     * @param findings normalized confirmed and uncertain findings
     * @param context scan context
     * @return inventory with confirmed items and uncertain findings separated
     */
    ConfigInventory build(List<ScanFinding> findings, ScanContext context);
}
