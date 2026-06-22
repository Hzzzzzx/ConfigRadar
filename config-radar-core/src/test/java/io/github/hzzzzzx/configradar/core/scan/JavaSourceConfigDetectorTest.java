package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.JavaSystemPropertyDetails;
import io.github.hzzzzzx.configradar.core.model.SpringConfigurationPropertiesDetails;
import io.github.hzzzzzx.configradar.core.model.SpringPlaceholderDetails;
import io.github.hzzzzzx.configradar.core.rule.AnnotationRule;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.rule.MethodCallRule;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaSourceConfigDetectorTest {
    @Test
    void detectsSpringAnnotationsAndGetPropertyCalls() throws Exception {
        var findings = detect();

        assertTrue(findings.stream().anyMatch(item -> item.key().equals("payment.timeout")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("java.shell.default")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("empty.default")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("db.host")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("db.port")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("client")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("redisson.client")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("feature.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("feature.beta")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("db.url")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spel.timeout")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("SPEL_SECRET")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spel.mode")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("http.timeout")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("resolver.endpoint")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("resolver.required")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("cache.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("client.pool")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("app.mode")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("APP_SECRET")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("MAP_SECRET")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("MAP_REGION")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("ENV_FEATURE_FLAG")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("legacy.port")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("legacy.limit")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("legacy.enabled")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("runtime.region")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.main.banner-mode")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("management.endpoints.web.exposure.include")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.lifecycle.timeout-per-shutdown-phase")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("jobs.cleanup.cron")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("jobs.cleanup.delay")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("custom.placeholder.default")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.profiles")));
        assertTrue(findings.stream().anyMatch(item -> item.key().equals("spring.property-source")));
    }

    @Test
    void recordsTypedDetailsAndDefaults() throws Exception {
        var findings = detect();

        var value = finding(findings, "payment.timeout");
        assertEquals(FindingRole.READ, value.role());
        assertEquals("30", value.defaultValue().raw());
        assertInstanceOf(SpringPlaceholderDetails.class, value.details());
        assertNotNull(value.source().line());
        assertEquals("fallback", finding(findings, "java.shell.default").defaultValue().raw());
        assertEquals("", finding(findings, "empty.default").defaultValue().raw());

        assertEquals("localhost", finding(findings, "db.host").defaultValue().raw());
        assertEquals("5432", finding(findings, "db.port").defaultValue().raw());
        assertEquals(FindingRole.READ, finding(findings, "spel.timeout").role());
        assertEquals(FindingRole.READ, finding(findings, "SPEL_SECRET").role());
        assertEquals(FindingRole.READ, finding(findings, "spel.mode").role());

        var properties = finding(findings, "client");
        assertInstanceOf(SpringConfigurationPropertiesDetails.class, properties.details());

        var beanProperties = finding(findings, "redisson.client");
        var beanDetails = assertInstanceOf(SpringConfigurationPropertiesDetails.class, beanProperties.details());
        assertEquals("redissonClient", beanDetails.boundType());
        assertEquals(false, beanDetails.inferredFromFields());

        var system = finding(findings, "app.mode");
        assertEquals("local", system.defaultValue().raw());
        assertInstanceOf(JavaSystemPropertyDetails.class, system.details());

        var typedEnvironment = finding(findings, "http.timeout");
        assertEquals("2500", typedEnvironment.defaultValue().raw());
        assertInstanceOf(JavaSystemPropertyDetails.class, typedEnvironment.details());
        assertEquals("http://localhost", finding(findings, "resolver.endpoint").defaultValue().raw());
        assertNull(finding(findings, "resolver.required").defaultValue());

        assertEquals(FindingRole.READ, finding(findings, "cache.enabled").role());
        assertEquals(FindingRole.READ, finding(findings, "client.pool").role());

        var env = finding(findings, "APP_SECRET");
        assertEquals("APP_SECRET", env.key());
        assertInstanceOf(JavaSystemPropertyDetails.class, env.details());

        assertNull(finding(findings, "MAP_SECRET").defaultValue());
        assertEquals("cn", finding(findings, "MAP_REGION").defaultValue().raw());
        assertNull(finding(findings, "ENV_FEATURE_FLAG").defaultValue());

        var legacyPort = finding(findings, "legacy.port");
        assertEquals("8081", legacyPort.defaultValue().raw());
        assertInstanceOf(JavaSystemPropertyDetails.class, legacyPort.details());

        var legacyLimit = finding(findings, "legacy.limit");
        assertEquals("10", legacyLimit.defaultValue().raw());

        var legacyEnabled = finding(findings, "legacy.enabled");
        assertNull(legacyEnabled.defaultValue());
        assertInstanceOf(JavaSystemPropertyDetails.class, legacyEnabled.details());

        var runtimeRegion = finding(findings, "runtime.region");
        assertEquals(FindingRole.DEFINE, runtimeRegion.role());
        assertEquals("cn", runtimeRegion.value().raw());
        assertNull(runtimeRegion.defaultValue());

        var bannerMode = finding(findings, "spring.main.banner-mode");
        assertEquals(FindingRole.DEFINE, bannerMode.role());
        assertEquals("off", bannerMode.value().raw());

        var exposureInclude = finding(findings, "management.endpoints.web.exposure.include");
        assertEquals(FindingRole.DEFINE, exposureInclude.role());
        assertEquals("health,info", exposureInclude.value().raw());

        var shutdownPhase = finding(findings, "spring.lifecycle.timeout-per-shutdown-phase");
        assertEquals(FindingRole.DEFINE, shutdownPhase.role());
        assertEquals("20s", shutdownPhase.value().raw());

        assertEquals("0 0 * * * *", finding(findings, "jobs.cleanup.cron").defaultValue().raw());
        assertEquals("60000", finding(findings, "jobs.cleanup.delay").defaultValue().raw());
        assertEquals("no", finding(findings, "custom.placeholder.default").defaultValue().raw());
    }

    @Test
    void detectsConditionalOnPropertyAsCondition() throws Exception {
        var findings = detect();
        var condition = findings.stream()
            .filter(item -> item.key().equals("feature.enabled") && item.role() == FindingRole.CONDITION)
            .findFirst()
            .orElseThrow();

        assertEquals("true", condition.defaultValue().raw());

        var secondCondition = findings.stream()
            .filter(item -> item.key().equals("feature.beta") && item.role() == FindingRole.CONDITION)
            .findFirst()
            .orElseThrow();

        assertEquals("true", secondCondition.defaultValue().raw());
    }

    @Test
    void detectsProfileAnnotationAsMetadata() throws Exception {
        var profiles = detect().stream()
            .filter(item -> item.key().equals("spring.profiles"))
            .toList();

        assertEquals(2, profiles.size());
        assertTrue(profiles.stream().allMatch(item -> item.role() == FindingRole.METADATA));
        assertTrue(profiles.stream().anyMatch(item -> "prod".equals(item.environment().profile())));
        assertTrue(profiles.stream().anyMatch(item -> "staging".equals(item.environment().profile())));
    }

    @Test
    void detectsPropertySourceAnnotationAsMetadata() throws Exception {
        var propertySources = detect().stream()
            .filter(item -> item.key().equals("spring.property-source"))
            .toList();

        assertEquals(3, propertySources.size());
        assertTrue(propertySources.stream().allMatch(item -> item.role() == FindingRole.METADATA));
        assertTrue(propertySources.stream().anyMatch(item -> item.value().raw().equals("classpath:extra-client.properties")));
        assertTrue(propertySources.stream().anyMatch(item -> item.value().raw().equals("classpath:redis.properties")));
        assertTrue(propertySources.stream().anyMatch(item -> item.value().raw().equals("classpath:kafka.properties")));
    }

    @Test
    void exposesDynamicGetPropertyAsUncertain() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);

        var findings = new JavaSourceConfigDetector().detect(context);

        assertTrue(findings.stream().anyMatch(item -> item instanceof io.github.hzzzzzx.configradar.core.model.UncertainFinding));
    }

    @Test
    void appliesProjectMethodAndAnnotationRules() throws Exception {
        var rules = new ConfigRules(
            List.of(new MethodCallRule("custom-center", "ConfigCenter", "get", 0, 1, null)),
            List.of(new AnnotationRule("custom-annotation", "CustomConfigValue", "key", "defaultValue", null)),
            List.of()
        );
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, rules, index);

        var findings = new JavaSourceConfigDetector().detect(context).stream()
            .filter(ConfigFinding.class::isInstance)
            .map(ConfigFinding.class::cast)
            .toList();

        assertEquals("fallback", finding(findings, "custom.center").defaultValue().raw());
        assertEquals("yes", finding(findings, "custom.annotated").defaultValue().raw());
    }

    private static java.util.List<ConfigFinding> detect() throws Exception {
        var input = ScanInput.of(FixturePaths.springBasic());
        var options = ScanOptions.defaults();
        var index = new DefaultFileIndexer().index(input, options);
        var context = new ScanContext(input, options, ConfigRules.empty(), index);
        return new JavaSourceConfigDetector().detect(context).stream()
            .filter(ConfigFinding.class::isInstance)
            .map(ConfigFinding.class::cast)
            .toList();
    }

    private static ConfigFinding finding(java.util.List<ConfigFinding> findings, String key) {
        return findings.stream().filter(item -> item.key().equals(key)).findFirst().orElseThrow();
    }
}
