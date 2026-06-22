package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;

/** Compares two inventories. */
public interface ConfigDiffStrategy {
    /**
     * Stable diff strategy id.
     *
     * @return strategy id such as {@code key}, {@code full}, or {@code env}
     */
    String id();

    /**
     * Compares two inventories and returns a structured diff.
     *
     * @param base baseline inventory
     * @param head current inventory
     * @return diff result
     */
    ConfigDiff diff(ConfigInventory base, ConfigInventory head);
}
