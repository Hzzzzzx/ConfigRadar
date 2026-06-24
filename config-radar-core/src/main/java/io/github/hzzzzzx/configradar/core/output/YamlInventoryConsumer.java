package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;

/** Reference {@link InventoryConsumer} that writes the default ConfigRadar YAML inventory. */
public final class YamlInventoryConsumer implements InventoryConsumer {

    @Override
    public String id() {
        return "yaml";
    }

    @Override
    public void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception {
        try (var output = sink.openFile("config-inventory.yaml")) {
            YamlSupport.mapper().writeValue(output, inventory);
        }
    }
}
