package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.rule.ConfigFileRule;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SpringConfigFileDetectorTest {
    @TempDir
    Path tempDir;

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
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("server.shutdown-grace-period")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("datasource.url")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("DB_URL")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("nested.url")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("NESTED_URL")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("NESTED_HOST")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("payment.timeout")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("payment.endpoint")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("PAYMENT_ENDPOINT")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("feature.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.cloud.config.uri")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("CONFIG_SERVER_URL")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("imported.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("imported.secret")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("IMPORTED_SECRET")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.cloud.nacos.config.server-addr")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SPRING_PROFILES_ACTIVE")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SPRING_CONFIG_LOCATION")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SPRING_CONFIG_ADDITIONAL_LOCATION")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("REDIS_PASSWORD")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("FEATURE_FLAG")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("QUOTED_NAME")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SHELL_DEFAULT")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SPRING_APPLICATION_JSON")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.application.json")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("management.server.port")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("feature.json")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("file.json.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("INLINE_COMMENT")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("LOG_LEVEL")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("API_TOKEN")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("tool.mode")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("tool.timeout")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("dev.tool.enabled")));
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

        var nestedUrl = finding(findings, "NESTED_URL");
        assertEquals(FindingRole.READ, nestedUrl.role());
        assertEquals("${NESTED_HOST:-localhost}", nestedUrl.defaultValue().raw());
        var nestedHost = finding(findings, "NESTED_HOST");
        assertEquals(FindingRole.READ, nestedHost.role());
        assertEquals("localhost", nestedHost.defaultValue().raw());

        assertEquals(FindingRole.DEFINE, finding(findings, "payment.endpoint").role());

        var propertiesPlaceholder = finding(findings, "PAYMENT_ENDPOINT");
        assertEquals(FindingRole.READ, propertiesPlaceholder.role());
        assertEquals("https://pay.local", propertiesPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.PROPERTIES, propertiesPlaceholder.source().sourceKind());

        var bootstrapPlaceholder = finding(findings, "CONFIG_SERVER_URL");
        assertEquals(FindingRole.READ, bootstrapPlaceholder.role());
        assertEquals("http://localhost:8888", bootstrapPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.YAML, bootstrapPlaceholder.source().sourceKind());

        assertEquals(FindingRole.DEFINE, finding(findings, "imported.enabled").role());
        assertEquals(ValueType.BOOLEAN, finding(findings, "imported.enabled").value().type());
        assertEquals(FindingRole.DEFINE, finding(findings, "imported.secret").role());
        var importedPlaceholder = finding(findings, "IMPORTED_SECRET");
        assertEquals(FindingRole.READ, importedPlaceholder.role());
        assertEquals("secret", importedPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.PROPERTIES, importedPlaceholder.source().sourceKind());

        assertTrue(findings.stream()
            .anyMatch(item -> item.key().equals("REDIS_PASSWORD") && item.role() == FindingRole.DEFINE));

        var envPlaceholder = findings.stream()
            .filter(item -> item.key().equals("REDIS_PASSWORD") && item.role() == FindingRole.READ)
            .findFirst()
            .orElseThrow();
        assertEquals("local-secret", envPlaceholder.defaultValue().raw());
        assertEquals(SourceKind.PROPERTIES, envPlaceholder.source().sourceKind());
        assertEquals("true", finding(findings, "FEATURE_FLAG").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "FEATURE_FLAG").value().type());
        assertEquals("config radar", finding(findings, "QUOTED_NAME").value().raw());
        assertEquals("9090", finding(findings, "management.server.port").value().raw());
        assertEquals(ValueType.INTEGER, finding(findings, "management.server.port").value().type());
        assertEquals(ValueType.DURATION, finding(findings, "server.shutdown-grace-period").value().type());
        assertEquals("true", finding(findings, "feature.json").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "feature.json").value().type());
        assertEquals("true", finding(findings, "file.json.enabled").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "file.json.enabled").value().type());
        assertEquals("worker", finding(findings, "tool.mode").value().raw());
        assertEquals("PT5S", finding(findings, "tool.timeout").value().raw());
        assertEquals(ValueType.DURATION, finding(findings, "tool.timeout").value().type());
        assertEquals("true", finding(findings, "dev.tool.enabled").value().raw());
        assertEquals(ValueType.BOOLEAN, finding(findings, "dev.tool.enabled").value().type());
        assertEquals("enabled", finding(findings, "INLINE_COMMENT").value().raw());
        var shellDefault = findings.stream()
            .filter(item -> item.key().equals("SHELL_DEFAULT") && item.role() == FindingRole.READ)
            .findFirst()
            .orElseThrow();
        assertEquals("fallback", shellDefault.defaultValue().raw());

        assertEquals("prod", finding(findings, "LOG_LEVEL").environment().profile());

        var prodPlaceholder = findings.stream()
            .filter(item -> item.key().equals("API_TOKEN") && item.role() == FindingRole.READ)
            .findFirst()
            .orElseThrow();
        assertEquals("prod", prodPlaceholder.environment().profile());
        assertEquals("token", prodPlaceholder.defaultValue().raw());
    }

    @Test
    void appliesProjectConfigFileRules() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var rules = new ConfigRules(
            List.of(),
            List.of(),
            List.of(new ConfigFileRule("custom-file", "src/main/resources/custom-config.properties", FileType.PROPERTIES, Scope.RUNTIME))
        );
        var context = new ScanContext(input, options, rules, index);

        var findings = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertTrue(findings.stream().anyMatch(item -> item.key().equals("custom.file.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("custom.file.timeout")));
        assertEquals(Scope.RUNTIME, finding(findings, "custom.file.enabled").source().scope());
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
        assertEquals(FindingRole.METADATA, finding(findings, "SPRING_PROFILES_ACTIVE").role());
        assertEquals(FindingRole.METADATA, finding(findings, "SPRING_CONFIG_LOCATION").role());
        assertEquals(FindingRole.METADATA, finding(findings, "SPRING_CONFIG_ADDITIONAL_LOCATION").role());
    }

    @Test
    void keepsScanningWhenSpringApplicationJsonIsMalformed() throws Exception {
        Files.writeString(tempDir.resolve(".env"), """
            SPRING_APPLICATION_JSON={bad
            PLAIN_AFTER_BAD_JSON=ok
            """);
        var input = ScanInput.of(tempDir);
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new SpringConfigFileDetector().detect(context).stream()
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals("{bad", finding(findings, "SPRING_APPLICATION_JSON").value().raw());
        assertEquals("ok", finding(findings, "PLAIN_AFTER_BAD_JSON").value().raw());
    }

    private static ConfigFinding finding(List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
