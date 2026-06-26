package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigDiff;

/** Reference {@link DiffConsumer} that writes the default ConfigRadar YAML diff. */
public final class YamlDiffConsumer implements DiffConsumer {

    @Override
    public String id() {
        return "yaml";
    }

    @Override
    public void consume(ConfigDiff diff, ConsumerContext context, ConsumerSink sink) throws Exception {
        try (var output = sink.openFile("config-diff.yaml")) {
            YamlSupport.mapper().writeValue(output, diff);
        }
    }
}
