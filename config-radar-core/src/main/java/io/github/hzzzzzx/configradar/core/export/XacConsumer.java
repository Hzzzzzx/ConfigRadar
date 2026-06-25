package io.github.hzzzzzx.configradar.core.export;

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
 * headers, an application {@code metadata.name}, and all config under {@code data}. It uses 4-space
 * indentation to match the Kubernetes-manifest style the platform expects. It is partitioned:
 * non-sensitive keys go to {@code data.app_configs}, sensitive keys (password/secret/token/credential)
 * go to {@code data.J2C.secrets} with placeholder passwords derived from the key (underscore form),
 * since the real secret is provisioned out-of-band.
 */
public final class XacConsumer implements InventoryConsumer {

    private final AppConfigCenterExporter exporter = new AppConfigCenterExporter();

    /**
     * Dumps the manifest with 4-space indentation to match the XAC platform manifest style
     * (issue #3). Jackson YAML defaults to 2-space, and SnakeYAML does not recognize Java records,
     * so Jackson is first used to convert the record tree to a generic Map/List structure, then
     * SnakeYAML dumps that with a 4-space block style.
     */
    private static void dumpManifest(Object data, java.io.Writer writer) {
        // Step 1: Jackson converts records to a generic Map/List tree (handles records correctly).
        var mapper = io.github.hzzzzzx.configradar.core.io.YamlSupport.mapper();
        var generic = mapper.convertValue(data, java.util.Map.class);
        // Step 2: SnakeYAML dumps the generic tree with 4-space block indentation.
        var options = new org.yaml.snakeyaml.DumperOptions();
        options.setIndent(4);
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        new org.yaml.snakeyaml.Yaml(options).dump(generic, writer);
    }

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

        try (var out = sink.openFile("app-configs.yaml");
             var writer = new java.io.OutputStreamWriter(out)) {
            dumpManifest(output, writer);
        }
    }
}

