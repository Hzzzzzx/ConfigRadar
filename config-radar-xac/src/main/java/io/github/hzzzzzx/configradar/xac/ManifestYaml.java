package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import java.io.IOException;
import java.io.Writer;

/**
 * Dumps a data tree as a YAML manifest with 4-space block indentation (Kubernetes-manifest style).
 *
 * <p>Jackson YAML defaults to 2-space indentation and SnakeYAML cannot serialize Java records
 * directly. This combines both: Jackson converts the record tree to a generic {@code Map}/{@code List}
 * structure, then SnakeYAML dumps it with a 4-space block style.
 */
public final class ManifestYaml {
    private ManifestYaml() {
    }

    /**
     * Writes the data tree to the writer as 4-space block YAML.
     *
     * @param data   the data tree (may contain records, maps, lists)
     * @param writer destination writer; not closed by this method
     * @throws IOException when writing fails
     */
    public static void dump(Object data, Writer writer) throws IOException {
        var mapper = YamlSupport.mapper();
        var generic = mapper.convertValue(data, java.util.Map.class);
        var options = new org.yaml.snakeyaml.DumperOptions();
        options.setIndent(4);
        options.setDefaultFlowStyle(org.yaml.snakeyaml.DumperOptions.FlowStyle.BLOCK);
        new org.yaml.snakeyaml.Yaml(options).dump(generic, writer);
    }
}
