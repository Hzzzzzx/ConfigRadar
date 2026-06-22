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
        assertEquals("canary", finding(findings, "EXTRA_MODE").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "EXTRA_ENABLED").value().type());
        assertEquals("local", finding(findings, "secret.mode").value().raw());
        var secretData = finding(findings, "kubernetes.secret.data.app-secret.token");
        assertEquals(FindingRole.METADATA, secretData.role());
        assertEquals("token", secretData.value().raw());
        var secretDataDetails = assertInstanceOf(ExternalDetails.class, secretData.details());
        assertEquals("secret-data-key", secretDataDetails.type());
        assertEquals("prod", finding(findings, "K8S_APP_MODE").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "K8S_FEATURE_ENABLED").value().type());
        assertEquals("app-config:k8s.config.limit", finding(findings, "K8S_CONFIG_LIMIT").value().raw());
        var configRef = assertInstanceOf(ExternalDetails.class, finding(findings, "K8S_CONFIG_LIMIT").details());
        assertEquals("config-map-key-ref", configRef.type());
        assertEquals("app-secret:token", finding(findings, "K8S_SECRET_TOKEN").value().raw());
        var secretRef = assertInstanceOf(ExternalDetails.class, finding(findings, "K8S_SECRET_TOKEN").details());
        assertEquals("secret-key-ref", secretRef.type());
        var envFromConfigMap = finding(findings, "kubernetes.env-from.config-map.app-extra-config");
        assertEquals(FindingRole.METADATA, envFromConfigMap.role());
        assertEquals("app-extra-config", envFromConfigMap.value().raw());
        var envFromConfigMapDetails = assertInstanceOf(ExternalDetails.class, envFromConfigMap.details());
        assertEquals("env-from-config-map-ref", envFromConfigMapDetails.type());
        assertEquals("canary", finding(findings, "APP_EXTRA_MODE").value().raw());
        var expanded = assertInstanceOf(ExternalDetails.class, finding(findings, "APP_EXTRA_MODE").details());
        assertEquals("env-from-config-map-data", expanded.type());
        assertEquals(ValueType.BOOLEAN, finding(findings, "APP_EXTRA_ENABLED").value().type());
        var envFromSecret = finding(findings, "kubernetes.env-from.secret.app-secret");
        assertEquals(FindingRole.METADATA, envFromSecret.role());
        assertEquals("app-secret", envFromSecret.value().raw());
        var envFromSecretDetails = assertInstanceOf(ExternalDetails.class, envFromSecret.details());
        assertEquals("env-from-secret-ref", envFromSecretDetails.type());
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
