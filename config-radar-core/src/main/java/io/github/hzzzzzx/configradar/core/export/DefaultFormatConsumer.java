package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.output.InventoryConsumer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference {@link InventoryConsumer} for the {@code default} format: a plain config inventory.
 *
 * <p>Every key (including sensitive ones) goes into a single {@code app_configs} list; sensitive
 * keys are flagged with {@code secret: 1}. No J2C section. This is the general-purpose config
 * statistic, independent of any specific deployment platform.
 */
public final class DefaultFormatConsumer implements InventoryConsumer {

    private final AppConfigCenterExporter exporter = new AppConfigCenterExporter();

    @Override
    public String id() {
        return "default";
    }

    @Override
    public void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception {
        var result = exporter.export(inventory, AppConfigCenterExporter.ExportFormat.DEFAULT);
        var output = new LinkedHashMap<String, Object>();
        output.put("app_configs", result.entries());
        try (var out = sink.openFile("app-configs.yaml")) {
            YamlSupport.mapper().writeValue(out, output);
        }
    }
}
