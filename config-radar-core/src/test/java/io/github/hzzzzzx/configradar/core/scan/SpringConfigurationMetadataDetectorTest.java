package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SpringConfigurationMetadataDetectorTest {
    @Test
    void detectsSpringConfigurationMetadataProperties() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new SpringConfigurationMetadataDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        var timeout = finding(findings, "metadata.client.timeout");
        assertEquals(FindingRole.READ, timeout.role());
        assertEquals("3000", timeout.defaultValue().raw());
        assertEquals(ValueType.INTEGER, timeout.defaultValue().type());
        assertEquals(SourceKind.JSON, timeout.source().sourceKind());

        var enabled = finding(findings, "metadata.client.enabled");
        assertEquals("true", enabled.defaultValue().raw());
        assertEquals(ValueType.BOOLEAN, enabled.defaultValue().type());
    }

    private static ConfigFinding finding(List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
