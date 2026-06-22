package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.JavaSystemPropertyDetails;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainReason;
import io.github.hzzzzzx.configradar.core.model.UnknownUncertainDetails;
import io.github.hzzzzzx.configradar.core.output.YamlInventoryConsumer;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScanPipelineTest {
    @Test
    void pipelineSplitsConfirmedAndUncertainFindings() throws Exception {
        var detector = new ConfigDetector() {
            @Override
            public String id() {
                return "test-detector";
            }

            @Override
            public List<io.github.hzzzzzx.configradar.core.model.ScanFinding> detect(ScanContext context) {
                var source = new SourceLocation("src/main/java/App.java", 10, "App", SourceKind.JAVA, Scope.MAIN);
                return List.of(
                    new ConfigFinding(
                        "payment.timeout",
                        "payment.timeout",
                        FindingRole.READ,
                        null,
                        null,
                        null,
                        source,
                        Confidence.HIGH,
                        id(),
                        new JavaSystemPropertyDetails(null, false)
                    ),
                    new UncertainFinding(
                        "env.getProperty(prefix + \".timeout\")",
                        UncertainReason.STRING_CONCAT,
                        "Environment.getProperty",
                        null,
                        source,
                        Confidence.LOW,
                        id(),
                        new UnknownUncertainDetails("env.getProperty(prefix + \".timeout\")")
                    )
                );
            }
        };

        var pipeline = new ScanPipeline(
            new DefaultFileIndexer(),
            new DetectorRegistry(List.of(detector)),
            List.of(new NoopFindingProcessor()),
            List.of(new NoopFindingNormalizer()),
            new DefaultInventoryBuilder(),
            List.of(new SummaryInventoryEnricher())
        );

        var result = pipeline.scan(
            ScanInput.of(FixturePaths.springBasic()),
            ScanOptions.defaults(),
            ConfigRules.empty()
        );

        assertEquals(1, result.inventory().items().size());
        assertEquals(1, result.inventory().uncertain().size());
        assertEquals(1, result.inventory().summary().keys());

        var out = new ByteArrayOutputStream();
        new YamlInventoryConsumer().write(result.inventory(), out);
        var yaml = out.toString();
        assertTrue(yaml.contains("items:"));
        assertTrue(yaml.contains("uncertain:"));
        assertTrue(yaml.contains("payment.timeout"));
    }

    @Test
    void detectorFailureBecomesDiagnosticAndScanContinues() throws Exception {
        var failing = new ConfigDetector() {
            @Override
            public String id() {
                return "failing-detector";
            }

            @Override
            public List<io.github.hzzzzzx.configradar.core.model.ScanFinding> detect(ScanContext context) {
                throw new IllegalStateException("boom");
            }
        };
        var succeeding = new ConfigDetector() {
            @Override
            public String id() {
                return "succeeding-detector";
            }

            @Override
            public List<io.github.hzzzzzx.configradar.core.model.ScanFinding> detect(ScanContext context) {
                var source = new SourceLocation("src/main/java/App.java", 11, "App", SourceKind.JAVA, Scope.MAIN);
                return List.of(new ConfigFinding(
                    "server.port",
                    "server.port",
                    FindingRole.READ,
                    null,
                    null,
                    null,
                    source,
                    Confidence.HIGH,
                    id(),
                    new JavaSystemPropertyDetails(null, false)
                ));
            }
        };

        var pipeline = new ScanPipeline(
            new DefaultFileIndexer(),
            new DetectorRegistry(List.of(failing, succeeding)),
            List.of(new NoopFindingProcessor()),
            List.of(new NoopFindingNormalizer()),
            new DefaultInventoryBuilder(),
            List.of(new SummaryInventoryEnricher())
        );

        var result = pipeline.scan(ScanInput.of(FixturePaths.springBasic()), ScanOptions.defaults(), ConfigRules.empty());

        assertEquals(1, result.inventory().items().size());
        assertEquals(1, result.diagnostics().size());
        assertEquals(DiagnosticSeverity.WARNING, result.diagnostics().get(0).severity());
        assertEquals("failing-detector", result.diagnostics().get(0).componentId());
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.phase().equals("detector-execution")));
    }

    @Test
    void runReportSerializesMetricsAndDiagnostics() throws Exception {
        var detector = new ConfigDetector() {
            @Override
            public String id() {
                return "failing-detector";
            }

            @Override
            public List<io.github.hzzzzzx.configradar.core.model.ScanFinding> detect(ScanContext context) {
                throw new IllegalStateException("boom");
            }
        };
        var pipeline = new ScanPipeline(
            new DefaultFileIndexer(),
            new DetectorRegistry(List.of(detector)),
            List.of(new NoopFindingProcessor()),
            List.of(new NoopFindingNormalizer()),
            new DefaultInventoryBuilder(),
            List.of(new SummaryInventoryEnricher())
        );

        var result = pipeline.scan(ScanInput.of(FixturePaths.springBasic()), ScanOptions.defaults(), ConfigRules.empty());
        var out = new ByteArrayOutputStream();
        io.github.hzzzzzx.configradar.core.io.YamlSupport.mapper().writeValue(out, result.report());
        var yaml = out.toString();

        assertTrue(yaml.contains("config-radar-run/v1"));
        assertTrue(yaml.contains("metrics:"));
        assertTrue(yaml.contains("diagnostics:"));
        assertTrue(yaml.contains("failing-detector"));
    }

    @Test
    void defaultPipelineFindsSpringConfigFiles() throws Exception {
        var result = ScanPipeline.defaults().scan(
            ScanInput.of(FixturePaths.springBasic()),
            ScanOptions.defaults(),
            ConfigRules.empty()
        );

        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.application.name")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("payment.timeout")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("payment.client.timeout")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("payment.endpoint") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("PAYMENT_ENDPOINT") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.cloud.config.uri") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("CONFIG_SERVER_URL") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.cloud.nacos.config.server-addr")
                && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("SPRING_PROFILES_ACTIVE") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("REDIS_PASSWORD") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("REDIS_PASSWORD") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("FEATURE_FLAG") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("QUOTED_NAME") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("SHELL_DEFAULT") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("LOG_LEVEL") && item.environment().profile().equals("prod")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("API_TOKEN") && item.role() == FindingRole.READ
                && item.environment().profile().equals("prod")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("logging.file.path") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("LOG_LEVEL") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("db.host")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("db.port")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("custom.placeholder.default")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("redisson.client")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("db.url")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("datasource.url") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("DB_URL") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spel.timeout") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("SPEL_SECRET") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spel.mode") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("http.timeout")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("resolver.endpoint")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("resolver.required")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("cache.enabled")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("client.pool")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("feature.beta") && item.role() == FindingRole.CONDITION));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("app.mode")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("APP_SECRET")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("legacy.port")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("legacy.limit")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("legacy.enabled")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("runtime.region") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.main.banner-mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("management.endpoints.web.exposure.include")
                && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.lifecycle.timeout-per-shutdown-phase")
                && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("jobs.cleanup.cron")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("jobs.cleanup.delay")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.property-source") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.config.import") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles.include") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().contains("prefix + \".url\"")));
        assertTrue(result.inventory().checks().stream()
            .anyMatch(item -> item.type().equals("dynamic-config-key")
                && item.severity() == DiagnosticSeverity.ERROR
                && item.message().contains("prefix + \".url\"")));
        assertEquals(52, result.inventory().summary().keys());
        assertEquals(1, result.inventory().summary().checks());
    }
}
