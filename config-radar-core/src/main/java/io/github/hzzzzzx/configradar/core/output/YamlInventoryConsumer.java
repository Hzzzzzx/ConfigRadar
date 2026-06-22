package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import java.io.IOException;
import java.io.OutputStream;

/** Default inventory YAML writer. */
public final class YamlInventoryConsumer implements InventoryConsumer {
    @Override
    public String id() {
        return "yaml";
    }

    @Override
    public void write(ConfigInventory inventory, OutputStream output) throws IOException {
        YamlSupport.mapper().writeValue(output, inventory);
    }
}
