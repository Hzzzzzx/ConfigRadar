package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LogbackSpringXmlDetectorTest {
    @Test
    void detectsSpringPropertyAndPlaceholders() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new LogbackSpringXmlDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.READ, finding(findings, "spring.application.name").role());
        assertEquals("spring-basic", finding(findings, "spring.application.name").defaultValue().raw());
        assertEquals(SourceKind.XML, finding(findings, "spring.application.name").source().sourceKind());
        assertEquals("./logs", finding(findings, "logging.file.path").defaultValue().raw());
        assertEquals("INFO", finding(findings, "LOG_LEVEL").defaultValue().raw());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
