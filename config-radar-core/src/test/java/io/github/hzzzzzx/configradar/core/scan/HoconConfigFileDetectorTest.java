package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HoconConfigFileDetectorTest {
    @Test
    void detectsSimpleApplicationConfPairs() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new HoconConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.DEFINE, finding(findings, "typesafe.file.mode").role());
        assertEquals("prod", finding(findings, "typesafe.file.mode").value().raw());
        assertEquals("true", finding(findings, "typesafe.file.enabled").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "typesafe.file.enabled").value().type());
        assertEquals("5", finding(findings, "typesafe.file.limit").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "typesafe.file.limit").value().type());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
