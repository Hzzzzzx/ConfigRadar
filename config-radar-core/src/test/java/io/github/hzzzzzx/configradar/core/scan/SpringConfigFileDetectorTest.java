package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.rule.ConfigFileRule;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpringConfigFileDetectorTest {
    @Test
    void detectsYamlAndPropertiesDefinitions() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.application.name")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("server.port")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("datasource.url")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("DB_URL")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("payment.timeout")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("payment.endpoint")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("PAYMENT_ENDPOINT")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("feature.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.cloud.config.uri")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("CONFIG_SERVER_URL")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.cloud.nacos.config.server-addr")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SPRING_PROFILES_ACTIVE")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("REDIS_PASSWORD")));
    }

    @Test
    void fillsRoleValueProfileAndSourceEvidence() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var payment = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .filter(item -> item.key().equals("payment.timeout"))
            .findFirst()
            .orElseThrow();

        assertEquals(FindingRole.DEFINE, payment.role());
        assertEquals("dev", payment.environment().profile());
        assertEquals("30", payment.value().raw());
        assertEquals(ValueType.INTEGER, payment.value().type());
        assertEquals(SourceKind.PROPERTIES, payment.source().sourceKind());
        assertNotNull(payment.source().line());
    }

    @Test
    void exposesPlaceholdersInsideConfigValuesAsReads() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.DEFINE, finding(findings, "datasource.url").role());

        var placeholder = finding(findings, "DB_URL");
        assertEquals(FindingRole.READ, placeholder.role());
        assertEquals("jdbc:postgresql://localhost:5432/app", placeholder.defaultValue().raw());
        assertEquals(SourceKind.YAML, placeholder.source().sourceKind());

        assertEquals(FindingRole.DEFINE, finding(findings, "payment.endpoint").role());

        var propertiesPlaceholder = finding(findings, "PAYMENT_ENDPOINT");
        assertEquals(FindingRole.READ, propertiesPlaceholder.role());
        assertEquals("https://pay.local", propertiesPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.PROPERTIES, propertiesPlaceholder.source().sourceKind());

        var bootstrapPlaceholder = finding(findings, "CONFIG_SERVER_URL");
        assertEquals(FindingRole.READ, bootstrapPlaceholder.role());
        assertEquals("http://localhost:8888", bootstrapPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.YAML, bootstrapPlaceholder.source().sourceKind());

        assertTrue(findings.stream()
            .anyMatch(item -> item.key().equals("REDIS_PASSWORD") && item.role() == FindingRole.DEFINE));

        var envPlaceholder = findings.stream()
            .filter(item -> item.key().equals("REDIS_PASSWORD") && item.role() == FindingRole.READ)
            .findFirst()
            .orElseThrow();
        assertEquals("local-secret", envPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.PROPERTIES, envPlaceholder.source().sourceKind());
    }

    @Test
    void appliesProjectConfigFileRules() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var rules = new ConfigRules(
            List.of(),
            List.of(),
            List.of(new ConfigFileRule("custom-file", "src/main/resources/custom-config.properties", FileType.PROPERTIES, Scope.MAIN))
        );
        var context = new ScanContext(input, options, rules, index);

        var findings = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertTrue(findings.stream().anyMatch(item -> item.key().equals("custom.file.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("custom.file.timeout")));
    }

    @Test
    void appliesYamlDocumentProfileToFindings() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var endpoint = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .filter(item -> item.key().equals("client.endpoint"))
            .findFirst()
            .orElseThrow();

        assertEquals("prod", endpoint.environment().profile());
    }

    @Test
    void marksSpringProfileActivationAsMetadata() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals(FindingRole.METADATA, finding(findings, "spring.config.activate.on-profile").role());
        assertEquals(FindingRole.METADATA, finding(findings, "spring.config.import").role());
        assertEquals(FindingRole.METADATA, finding(findings, "spring.profiles.include").role());
    }

    private static ConfigFinding finding(List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
