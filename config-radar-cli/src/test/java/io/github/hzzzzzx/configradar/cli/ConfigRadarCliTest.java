package io.github.hzzzzzx.configradar.cli;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainReason;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.model.UnknownUncertainDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigRadarCliTest {
    @TempDir
    private Path tempDir;

    @Test
    void inventoryCommandWritesInventoryAndMetrics() throws Exception {
        var inventory = tempDir.resolve("inventory.yaml");
        var metrics = tempDir.resolve("metrics.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            springBasic().toString(),
            "-o",
            inventory.toString(),
            "--metrics",
            metrics.toString()
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(inventory));
        assertTrue(Files.exists(metrics));
        var inventoryYaml = Files.readString(inventory);
        assertTrue(inventoryYaml.contains("config-inventory/v1"));
        assertTrue(inventoryYaml.contains("spring.application.name"));
        assertTrue(inventoryYaml.contains("payment.timeout"));
        assertTrue(inventoryYaml.contains("app.mode"));
        assertTrue(inventoryYaml.contains("uncertain:"));
        assertTrue(inventoryYaml.contains("checks:"));
        assertTrue(inventoryYaml.contains("dynamic-config-key"));
        assertTrue(inventoryYaml.contains("ERROR"));
        assertTrue(Files.readString(metrics).contains("config-radar-run/v1"));
    }

    @Test
    void inventoryCommandAppliesRulesFile() throws Exception {
        var inventory = tempDir.resolve("inventory-with-rules.yaml");
        var rules = tempDir.resolve("config-radar-rules.yaml");
        Files.writeString(rules, rulesYaml());

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            springBasic().toString(),
            "-o",
            inventory.toString(),
            "--rules",
            rules.toString()
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(inventory);
        assertTrue(yaml.contains("custom.center"));
        assertTrue(yaml.contains("custom.annotated"));
        assertTrue(yaml.contains("custom.file.enabled"));
    }

    @Test
    void inventoryCommandLoadsDefaultProjectRulesFile() throws Exception {
        var project = tempDir.resolve("spring-basic-copy");
        copyDirectory(springBasic(), project);
        Files.writeString(project.resolve("config-radar-rules.yaml"), rulesYaml());
        var inventory = tempDir.resolve("inventory-default-rules.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            project.toString(),
            "-o",
            inventory.toString()
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(inventory);
        assertTrue(yaml.contains("custom.center"));
        assertTrue(yaml.contains("custom.annotated"));
        assertTrue(yaml.contains("custom.file.enabled"));
    }

    @Test
    void inventoryCommandAcceptsCodegraphFlag() throws Exception {
        var project = tempDir.resolve("spring-basic-codegraph");
        copyDirectory(springBasic(), project);
        var inventory = tempDir.resolve("inventory-codegraph.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            project.toString(),
            "-o",
            inventory.toString(),
            "--enable-codegraph"
        );

        assertEquals(0, exitCode);
        assertTrue(Files.exists(inventory));
    }

    @Test
    void inventoryCommandAcceptsParallelism() throws Exception {
        var inventory = tempDir.resolve("inventory-parallel.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            springBasic().toString(),
            "-o",
            inventory.toString(),
            "--parallelism",
            "2"
        );

        assertEquals(0, exitCode);
        assertTrue(Files.readString(inventory).contains("config-inventory/v1"));
    }

    @Test
    void inventoryCommandAppliesIncludeAndExcludePaths() throws Exception {
        var inventory = tempDir.resolve("inventory-filtered.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            springBasic().toString(),
            "-o",
            inventory.toString(),
            "--include",
            "src/main/resources",
            "--exclude",
            "src/main/resources/logback-spring.xml"
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(inventory);
        assertTrue(yaml.contains("spring.application.name"));
        assertTrue(yaml.contains("log4j2.file.path"));
        assertFalse(yaml.contains("app.mode"));
        assertFalse(yaml.contains("LOG_LEVEL"));
    }

    @Test
    void inventoryCommandAppliesEnvironmentHints() throws Exception {
        var inventory = tempDir.resolve("inventory-env.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            springBasic().toString(),
            "-o",
            inventory.toString(),
            "--profile",
            "dev",
            "--region",
            "cn",
            "--namespace",
            "blue"
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(inventory);
        assertTrue(yaml.contains("profile: \"dev\""));
        assertTrue(yaml.contains("region: \"cn\""));
        assertTrue(yaml.contains("namespace: \"blue\""));
    }

    @Test
    void inventoryCommandCanRedactSensitiveValues() throws Exception {
        var inventory = tempDir.resolve("inventory-redacted.yaml");

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "inventory",
            springBasic().toString(),
            "-o",
            inventory.toString(),
            "--redact-sensitive"
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(inventory);
        assertTrue(yaml.contains("redis.password"));
        assertTrue(yaml.contains("******"));
        assertFalse(yaml.contains("redis-secret"));
    }

    @Test
    void diffCommandWritesKeyBasedDiff() throws Exception {
        var base = tempDir.resolve("base.yaml");
        var head = tempDir.resolve("head.yaml");
        var output = tempDir.resolve("diff.yaml");
        var mapper = YamlSupport.mapper();
        mapper.writeValue(base.toFile(), inventory(item("server.port", "8080")));
        mapper.writeValue(head.toFile(), inventoryWithUncertain(
            List.of(item("server.port", "9090"), item("feature.enabled", "true")),
            List.of(uncertain("environment.getProperty(prefix + \".url\")"))
        ));

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "diff",
            "--base",
            base.toString(),
            "--head",
            head.toString(),
            "-o",
            output.toString()
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(output);
        assertTrue(yaml.contains("config-diff/v1"));
        assertTrue(yaml.contains("feature.enabled"));
        assertTrue(yaml.contains("oldValue: \"8080\""));
        assertTrue(yaml.contains("newValue: \"9090\""));
        assertTrue(yaml.contains("checks:"));
        assertTrue(yaml.contains("dynamic-config-key"));
    }

    @Test
    void diffCommandCanRedactSensitiveValues() throws Exception {
        var base = tempDir.resolve("base-secret.yaml");
        var head = tempDir.resolve("head-secret.yaml");
        var output = tempDir.resolve("diff-secret.yaml");
        var mapper = YamlSupport.mapper();
        mapper.writeValue(base.toFile(), inventory(item("redis.password", "old-secret")));
        mapper.writeValue(head.toFile(), inventory(item("redis.password", "new-secret")));

        int exitCode = new CommandLine(new ConfigRadarCli()).execute(
            "diff",
            "--base",
            base.toString(),
            "--head",
            head.toString(),
            "-o",
            output.toString(),
            "--redact-sensitive"
        );

        assertEquals(0, exitCode);
        var yaml = Files.readString(output);
        assertTrue(yaml.contains("config-diff/v1"));
        assertFalse(yaml.contains("old-secret"));
        assertFalse(yaml.contains("new-secret"));
    }

    private static Path springBasic() {
        var root = Path.of("fixtures/spring-basic");
        return Files.exists(root) ? root : Path.of("../fixtures/spring-basic");
    }

    private static String rulesYaml() {
        return """
            methodCalls:
              - id: custom-center
                owner: ConfigCenter
                method: get
                keyArg: 0
                defaultArg: 1
            annotations:
              - id: custom-annotation
                type: CustomConfigValue
                keyAttribute: key
                defaultAttribute: defaultValue
            configFiles:
              - id: custom-file
                pattern: src/main/resources/custom-config.properties
                format: PROPERTIES
                scope: MAIN
            """;
    }

    private static void copyDirectory(Path source, Path target) throws Exception {
        try (var paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                var destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, null, null, List.of(items), List.of(), List.of(), List.of());
    }

    private static ConfigInventory inventoryWithUncertain(List<ConfigFinding> items, List<UncertainFinding> uncertain) {
        return new ConfigInventory(null, null, null, items, uncertain, List.of(), List.of());
    }

    private static UncertainFinding uncertain(String expression) {
        return new UncertainFinding(
            expression,
            UncertainReason.STRING_CONCAT,
            "Environment.getProperty",
            EnvironmentContext.none(),
            new SourceLocation("App.java", 1, "App", SourceKind.JAVA, Scope.MAIN),
            Confidence.LOW,
            "test",
            new UnknownUncertainDetails(expression)
        );
    }

    private static ConfigFinding item(String key, String value) {
        return new ConfigFinding(
            key,
            key,
            FindingRole.DEFINE,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            EnvironmentContext.none(),
            new SourceLocation("application.yml", 1, null, SourceKind.YAML, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", key)
        );
    }
}
