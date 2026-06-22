package io.github.hzzzzzx.configradar.core.output;

import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.scan.FixturePaths;
import io.github.hzzzzzx.configradar.core.scan.ScanInput;
import io.github.hzzzzzx.configradar.core.scan.ScanOptions;
import io.github.hzzzzzx.configradar.core.scan.ScanPipeline;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class YamlInventoryConsumerTest {
    @Test
    void writesInventoryYaml() throws Exception {
        var result = ScanPipeline.defaults().scan(
            ScanInput.of(FixturePaths.springBasic()),
            ScanOptions.defaults(),
            ConfigRules.empty()
        );
        var output = new ByteArrayOutputStream();

        new YamlInventoryConsumer().write(result.inventory(), output);

        var yaml = output.toString();
        assertTrue(yaml.contains("config-inventory/v1"));
        assertTrue(yaml.contains("items:"));
        assertTrue(yaml.contains("spring.application.name"));
    }
}
