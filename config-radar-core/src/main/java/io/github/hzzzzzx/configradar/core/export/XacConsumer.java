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
 * <p>The output follows the platform's manifest shape (issue #3): fixed {@code apiVersion}/{@code kind}
 * headers, an application {@code metadata.name}, and all config under {@code data}. It is
 * partitioned: non-sensitive keys go to {@code data.app_configs}, sensitive keys
 * (password/secret/token/credential) go to {@code data.J2C.secrets} with placeholder passwords
 * derived from the key (underscore form), since the real secret is provisioned out-of-band.
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

        var data = new LinkedHashMap<String, Object>();
        data.put("app_configs", result.entries());
        if (!result.secrets().isEmpty()) {
            data.put("J2C", Map.of("secrets", result.secrets()));
        }

        var output = new LinkedHashMap<String, Object>();
        output.put("apiVersion", "com.huawei.his.appconfigcenter.v3");
        output.put("kind", "his.appconfigcenter");
        // Application name: explicit -D name=... first, else the scanned project name, else placeholder.
        var appName = context.property("name");
        if (appName == null || appName.isBlank()) {
            var project = inventory.project();
            appName = (project != null && !"unknown".equals(project.name())) ? project.name() : "app";
        }
        output.put("metadata", Map.of("name", appName));
        output.put("data", data);

        try (var out = sink.openFile("app-configs.yaml")) {
            YamlSupport.mapper().writeValue(out, output);
        }
    }
}
