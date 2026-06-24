package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.output.ConsumerSink;
import io.github.hzzzzzx.configradar.core.output.InventoryConsumer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reference {@link InventoryConsumer} for the {@code xac} format: an artifact for the internal XAC
 * deployment platform.
 *
 * <p>The output is partitioned: non-sensitive keys go to {@code app_configs}, sensitive keys
 * (password/secret/token/credential) go to {@code J2C.secrets} with placeholder passwords derived
 * from the key (underscore form), since the real secret is provisioned out-of-band.
 */
public final class XacConsumer implements InventoryConsumer {

    private final AppConfigCenterExporter exporter = new AppConfigCenterExporter();

    @Override
    public String id() {
        return "xac";
    }

    @Override
    public void consume(ConfigInventory inventory, ConsumerContext context, ConsumerSink sink) throws Exception {
        var result = exporter.export(inventory, AppConfigCenterExporter.ExportFormat.XAC);
        var output = new LinkedHashMap<String, Object>();
        output.put("app_configs", result.entries());
        if (!result.secrets().isEmpty()) {
            output.put("J2C", Map.of("secrets", result.secrets()));
        }
        try (var out = sink.openFile("app-configs.yaml")) {
            YamlSupport.mapper().writeValue(out, output);
        }
    }
}
