package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.scan.FixturePaths;
import io.github.hzzzzzx.configradar.core.scan.ScanInput;
import io.github.hzzzzzx.configradar.core.scan.ScanOptions;
import io.github.hzzzzzx.configradar.core.scan.ScanPipeline;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class YamlInventoryConsumerTest {
    @Test
    void writesInventoryYamlViaSink() throws Exception {
        var result = ScanPipeline.defaults().scan(
            ScanInput.of(FixturePaths.springBasic()),
            ScanOptions.defaults(),
            ConfigRules.empty()
        );
        var sink = new MemoryConsumerSink();

        new YamlInventoryConsumer().consume(result.inventory(), ConsumerContext.empty(), sink);

        var yaml = sink.contents.get("config-inventory.yaml").toString();
        assertTrue(yaml.contains("config-inventory/v1"));
        assertTrue(yaml.contains("items:"));
        assertTrue(yaml.contains("spring.application.name"));
    }

    @Test
    void registryFindsRegisteredConsumer() {
        var registry = new ConsumerRegistry().register(new YamlInventoryConsumer());
        assertEquals("yaml", registry.find("yaml").orElseThrow().id());
        assertTrue(registry.find("missing").isEmpty());
    }

    /** In-memory sink for tests: captures each file's bytes. */
    static final class MemoryConsumerSink implements ConsumerSink {
        final Map<String, ByteArrayOutputStream> contents = new LinkedHashMap<>();

        @Override
        public OutputStream openFile(String fileName) {
            var bytes = new ByteArrayOutputStream();
            contents.put(fileName, bytes);
            return bytes;
        }
    }
}
