package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class DockerfileEnvDetectorTest {
    @Test
    void detectsDockerfileEnvDefinitions() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new DockerfileEnvDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.DEFINE, finding(findings, "DOCKER_APP_MODE").role());
        assertEquals("prod", finding(findings, "DOCKER_APP_MODE").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "DOCKER_FEATURE_ENABLED").value().type());
        assertEquals("8080", finding(findings, "DOCKER_SERVER_PORT").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "DOCKER_SERVER_PORT").value().type());
        assertEquals(
            "-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8",
            finding(findings, "DOCKER_JAVA_OPTS").value().raw()
        );
        assertEquals("prod", finding(findings, "docker.jvm.mode").value().raw());
        var jvmArg = assertInstanceOf(ExternalDetails.class, finding(findings, "docker.jvm.mode").details());
        assertEquals("command", jvmArg.type());
        assertEquals("worker", finding(findings, "docker.cli.mode").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "docker.cli.limit").value().type());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
