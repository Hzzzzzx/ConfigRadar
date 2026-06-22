package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import java.util.List;

/** Placeholder diff strategy until identity-based diff is implemented. */
public final class EmptyDiffStrategy implements ConfigDiffStrategy {
    @Override
    public String id() {
        return "empty";
    }

    /**
     * Produces a valid empty diff while the real identity strategy is still being designed.
     *
     * @param base baseline inventory, currently only used to prove command wiring
     * @param head current inventory, currently only used to prove command wiring
     * @return schema-compatible diff with no changes
     */
    @Override
    public ConfigDiff diff(ConfigInventory base, ConfigInventory head) {
        return new ConfigDiff(ConfigDiff.SCHEMA_VERSION, null, List.of(), List.of(), List.of(), List.of());
    }
}
