package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
        assertEquals("-Dcompose.env.jvm.mode=blue", finding(findings, "COMPOSE_JAVA_OPTS").value().raw());
        assertEquals("blue", finding(findings, "compose.env.jvm.mode").value().raw());
        var envJvmArg = assertInstanceOf(ExternalDetails.class, finding(findings, "compose.env.jvm.mode").details());
        assertEquals("environment-jvm-arg", envJvmArg.type());
        assertEquals("4", finding(findings, "COMPOSE_WORKER_THREADS").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "COMPOSE_WORKER_THREADS").value().type());
        assertEquals("debug", finding(findings, "COMPOSE_LOG_LEVEL").value().raw());
        assertEquals("prod", finding(findings, "compose.jvm.mode").value().raw());
        var jvmArg = assertInstanceOf(ExternalDetails.class, finding(findings, "compose.jvm.mode").details());
        assertEquals("entrypoint", jvmArg.type());
        assertEquals("worker", finding(findings, "compose.cli.mode").value().raw());
        var cliArg = assertInstanceOf(ExternalDetails.class, finding(findings, "compose.cli.mode").details());
        assertEquals("command", cliArg.type());
        assertEquals(ValueType.INTEGER, finding(findings, "compose.cli.limit").value().type());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
