package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DockerComposeEnvDetectorTest {
    @Test
    void detectsComposeEnvironmentDefinitions() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new DockerComposeEnvDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.DEFINE, finding(findings, "COMPOSE_APP_MODE").role());
        assertEquals("prod", finding(findings, "COMPOSE_APP_MODE").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "COMPOSE_FEATURE_ENABLED").value().type());
        assertEquals("4", finding(findings, "COMPOSE_WORKER_THREADS").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "COMPOSE_WORKER_THREADS").value().type());
        assertEquals("debug", finding(findings, "COMPOSE_LOG_LEVEL").value().raw());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
