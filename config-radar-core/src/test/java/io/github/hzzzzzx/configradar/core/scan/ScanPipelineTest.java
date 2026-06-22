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
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.output.YamlInventoryConsumer;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.phase().equals("detector:failing-detector")));
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.phase().equals("detector:succeeding-detector")));
    }

    @Test
    void runsDetectorsWithConfiguredParallelism() throws Exception {
        var started = new CountDownLatch(2);
        var source = new SourceLocation("src/main/java/App.java", 1, "App", SourceKind.JAVA, Scope.MAIN);
        var pipeline = new ScanPipeline(
            new DefaultFileIndexer(),
            new DetectorRegistry(List.of(
                waitingDetector("first", "parallel.first", started, source),
                waitingDetector("second", "parallel.second", started, source)
            )),
            List.of(new NoopFindingProcessor()),
            List.of(new NoopFindingNormalizer()),
            new DefaultInventoryBuilder(),
            List.of(new SummaryInventoryEnricher())
        );

        var result = pipeline.scan(
            ScanInput.of(FixturePaths.springBasic()),
            new ScanOptions(false, true, 2, 0, null),
            ConfigRules.empty()
        );

        assertEquals(2, result.inventory().items().size());
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.phase().equals("detector:first")));
        assertTrue(result.metrics().stream().anyMatch(metric -> metric.phase().equals("detector:second")));
    }

    private static ConfigDetector waitingDetector(
        String id,
        String key,
        CountDownLatch started,
        SourceLocation source
    ) {
        return new ConfigDetector() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<io.github.hzzzzzx.configradar.core.model.ScanFinding> detect(ScanContext context)
                throws InterruptedException {
                started.countDown();
                if (!started.await(2, TimeUnit.SECONDS)) {
                    return List.of();
                }
                return List.of(new ConfigFinding(
                    key,
                    key,
                    FindingRole.READ,
                    null,
                    null,
                    null,
                    source,
                    Confidence.HIGH,
                    id,
                    new JavaSystemPropertyDetails(null, false)
                ));
            }
        };
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
            .anyMatch(item -> item.key().equals("java.shell.default") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("empty.default") && item.role() == FindingRole.READ));
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
            .anyMatch(item -> item.key().equals("SPRING_PROFILES_ACTIVE") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("SPRING_CONFIG_LOCATION") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("SPRING_CONFIG_ADDITIONAL_LOCATION")
                && item.role() == FindingRole.METADATA));
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
            .anyMatch(item -> item.key().equals("SPRING_APPLICATION_JSON") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.application.json") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("management.server.port") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("server.shutdown-grace-period")
                && item.role() == FindingRole.DEFINE
                && item.value().type() == ValueType.DURATION));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("JAVA_TOOL_OPTIONS") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("JDK_JAVA_OPTIONS") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("tool.mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("tool.timeout")
                && item.role() == FindingRole.DEFINE
                && item.value().type() == ValueType.DURATION));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("dev.tool.enabled")
                && item.role() == FindingRole.DEFINE
                && item.value().type() == ValueType.BOOLEAN));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("feature.json") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("file.json.enabled") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("INLINE_COMMENT") && item.role() == FindingRole.DEFINE));
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
            .anyMatch(item -> item.key().equals("log4j2.file.path") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("LOG4J_LEVEL") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("db.host")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("db.port")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("custom.placeholder.default")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("metadata.client.timeout") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("metadata.client.enabled") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("metadata.client.ttl") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("feature.expression.enabled") && item.role() == FindingRole.CONDITION));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("expression.mode") && item.role() == FindingRole.CONDITION));
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
            .anyMatch(item -> item.key().equals("spel.method.timeout") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("SPEL_METHOD_SECRET") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spel.method.mode") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spel.method.raw") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("http.timeout")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("resolver.endpoint")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("resolver.required")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("resolved.placeholder") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("resolver.placeholder.required") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("cache.enabled")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("client.pool")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("client.cache")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("feature.beta") && item.role() == FindingRole.CONDITION));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("app.mode")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("APP_SECRET")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("MAP_SECRET")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("MAP_REGION")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("ENV_FEATURE_FLAG")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.mode")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.flag")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.raw") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.default") && item.role() == FindingRole.READ));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.write") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.default-write") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.replaced") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.replaced-direct") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.set") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("map.property.removed") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("legacy.port")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("legacy.limit")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("legacy.enabled")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("runtime.region") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("runtime.mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.main.banner-mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("management.endpoints.web.exposure.include")
                && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.lifecycle.timeout-per-shutdown-phase")
                && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.builder.map") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.builder.entry") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.array.one") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.array.two") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("cli.mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("cli.timeout") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("cli.array.mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("cli.array.timeout") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("builder.cli.mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("builder.cli.timeout") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("builder.cli.array.mode") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("builder.cli.array.timeout") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("programmatic.endpoint") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("programmatic.timeout") && item.role() == FindingRole.DEFINE));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("jobs.cleanup.cron")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("jobs.cleanup.delay")));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.METADATA
                && "blue".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.METADATA
                && "green".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.METADATA
                && "builder-prod".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.METADATA
                && "builder-blue".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.CONDITION));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles") && item.role() == FindingRole.CONDITION
                && "qa".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.property-source") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.property-source") && item.role() == FindingRole.METADATA
                && "classpath:named-extra.properties".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.property-source") && item.role() == FindingRole.METADATA
                && "classpath:named-programmatic.properties".equals(item.value().raw())));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.config.import") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().items().stream()
            .anyMatch(item -> item.key().equals("spring.profiles.include") && item.role() == FindingRole.METADATA));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().contains("prefix + \".url\"")));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().equals("args")
                && item.reason() == UncertainReason.COMMAND_LINE_ARGS));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().equals("args")
                && item.reason() == UncertainReason.COMMAND_LINE_ARGS
                && item.rootSink().contains("SpringApplicationBuilder")));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().contains("getRuntimeMXBean().getInputArguments()")
                && item.reason() == UncertainReason.COMMAND_LINE_ARGS));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().equals("replacementProperties")
                && item.reason() == UncertainReason.MAP_DRIVEN_KEY));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().equals("properties")
                && item.reason() == UncertainReason.MAP_DRIVEN_KEY));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().equals("defaultProperties")
                && item.reason() == UncertainReason.MAP_DRIVEN_KEY));
        assertTrue(result.inventory().uncertain().stream()
            .anyMatch(item -> item.expression().equals("operator.mode")
                && item.reason() == UncertainReason.USER_INPUT));
        assertTrue(result.inventory().checks().stream()
            .anyMatch(item -> item.type().equals("dynamic-config-key")
                && item.severity() == DiagnosticSeverity.ERROR
                && item.message().contains("prefix + \".url\"")));
        assertEquals(110, result.inventory().summary().keys());
        assertEquals(8, result.inventory().summary().checks());
    }
}
