package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class KubernetesEnvDetectorTest {
    @Test
    void detectsKubernetesConfigMapAndEnvDefinitions() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new KubernetesEnvDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.DEFINE, finding(findings, "k8s.config.mode").role());
        assertEquals("prod", finding(findings, "k8s.config.mode").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "k8s.config.limit").value().type());
        assertEquals("prod", finding(findings, "K8S_APP_MODE").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "K8S_FEATURE_ENABLED").value().type());
        assertEquals("app-config:k8s.config.limit", finding(findings, "K8S_CONFIG_LIMIT").value().raw());
        var configRef = assertInstanceOf(ExternalDetails.class, finding(findings, "K8S_CONFIG_LIMIT").details());
        assertEquals("config-map-key-ref", configRef.type());
        assertEquals("app-secret:token", finding(findings, "K8S_SECRET_TOKEN").value().raw());
        var secretRef = assertInstanceOf(ExternalDetails.class, finding(findings, "K8S_SECRET_TOKEN").details());
        assertEquals("secret-key-ref", secretRef.type());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
