package io.github.hzzzzzx.configradar.core.export;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AppConfigCenterExporterTest {
    private final AppConfigCenterExporter exporter = new AppConfigCenterExporter();

    @Test
    void keepsHighestPriorityDefinitionWhenKeyDefinedMultipleTimes() {
        // db.host defined in application.yml (priority 70) AND application-prod.yml (priority 80).
        var inventory = inventory(
            define("db.host", "localhost", "src/main/resources/application.yml", null, SourceKind.YAML),
            define("db.host", "prod-host", "src/main/resources/application-prod.yml", "prod", SourceKind.YAML)
        );

        var result = exporter.export(inventory);

        assertEquals(1, result.entries().size());
        assertEquals("prod-host", result.entries().getFirst().config_value());
        // profile-specific file wins over the default application.yml.
    }

    @Test
    void applicationYmlBeatsBootstrapYml() {
        var inventory = inventory(
            define("app.name", "from-bootstrap", "src/main/resources/bootstrap.yml", null, SourceKind.YAML),
            define("app.name", "from-application", "src/main/resources/application.yml", null, SourceKind.YAML)
        );

        var result = exporter.export(inventory);

        assertEquals("from-application", result.entries().getFirst().config_value());
    }

    @Test
    void routesSensitiveKeysToJ2cSecrets() {
        // db.password is sensitive -> goes to the J2C secrets section, not app_configs.
        var inventory = inventory(
            define("db.password", "secret123", "src/main/resources/application.yml", null, SourceKind.YAML)
        );

        var result = exporter.export(inventory);

        assertTrue(result.entries().isEmpty(), "sensitive key is not a plain app_config");
        assertEquals(1, result.secrets().size());
        var secret = result.secrets().getFirst();
        assertEquals("db_password", secret.key(), "J2C key is the underscore form");
        assertEquals("${db_password}", secret.password(), "password is a placeholder from the key");
        assertEquals(AppConfigCenterExporter.DEFAULT_INIT_SOURCE, secret.init_source());
        assertEquals(AppConfigCenterExporter.DEFAULT_ENCRYPT_TYPE, secret.encrypt_type());
        assertEquals("mysql", secret.type(), "db.* key hints type mysql");
        assertEquals(AppConfigCenterExporter.DEFAULT_SCOPE, secret.scope());
    }

    @Test
    void mapsNonSensitiveKeyGroupAndSecretFlag() {
        var inventory = inventory(
            define("server.port", "8080", "src/main/resources/application.yml", null, SourceKind.YAML)
        );

        var result = exporter.export(inventory);

        var entry = result.entries().getFirst();
        assertEquals("server", entry.group_name());
        assertEquals("server.port", entry.config_key());
        assertEquals("8080", entry.config_value());
        assertEquals(0, entry.secret(), "non-sensitive key is not flagged");
        assertEquals(AppConfigCenterExporter.DEFAULT_SCOPE, entry.scope());
    }

    @Test
    void defaultFormatKeepsSensitiveKeysInAppConfigs() {
        // In DEFAULT mode the sensitive key stays in app_configs flagged secret:1, no J2C section.
        var inventory = inventory(
            define("db.password", "secret123", "src/main/resources/application.yml", null, SourceKind.YAML)
        );

        var result = exporter.export(inventory, AppConfigCenterExporter.ExportFormat.DEFAULT);

        assertTrue(result.secrets().isEmpty(), "DEFAULT mode has no J2C section");
        assertEquals(1, result.entries().size());
        var entry = result.entries().getFirst();
        assertEquals("db.password", entry.config_key());
        assertEquals("secret123", entry.config_value());
        assertEquals(1, entry.secret(), "sensitive key is flagged in DEFAULT mode");
    }

    @Test
    void groupsKeysByFirstSegment() {
        var inventory = inventory(
            define("server.port", "8080", "src/main/resources/application.yml", null, SourceKind.YAML),
            define("server.host", "0.0.0.0", "src/main/resources/application.yml", null, SourceKind.YAML),
            define("standalone", "yes", "src/main/resources/application.yml", null, SourceKind.YAML)
        );

        var result = exporter.export(inventory);

        var byKey = java.util.Map.of(
            "server.port", group(result, "server.port"),
            "standalone", group(result, "standalone")
        );
        assertEquals("server", byKey.get("server.port"));
        assertEquals("default", byKey.get("standalone"));
    }

    @Test
    void reportsMissingDefaultsForReadButUndefinedKeys() {
        // feature.flag is only READ (code), never DEFINED, no default -> missing.
        var inventory = new ConfigInventory(
            null, null, null,
            List.of(read("feature.flag", null)),
            List.of(), List.of(), List.of()
        );

        var result = exporter.export(inventory);

        assertTrue(result.entries().isEmpty(), "undefined key with no default is not a real entry");
        assertEquals(1, result.missing().size());
        assertEquals("feature.flag", result.missing().getFirst().config_key());
        assertEquals("", result.missing().getFirst().config_value());
    }

    @Test
    void keepsReadKeyWithDefaultAsEntryNotMissing() {
        // feature.timeout is only READ but has a default -> it is a real entry using the default.
        var inventory = new ConfigInventory(
            null, null, null,
            List.of(readWithDefault("feature.timeout", "5000")),
            List.of(), List.of(), List.of()
        );

        var result = exporter.export(inventory);

        assertEquals(1, result.entries().size());
        assertEquals("5000", result.entries().getFirst().config_value());
        assertTrue(result.missing().isEmpty());
    }

    @Test
    void mergeFillsInMissingValuesOverridingBase() {
        var base = List.of(
            entry("db.host", "localhost"),
            entry("feature.flag", null)
        );
        var filled = List.of(
            entry("feature.flag", "enabled")
        );

        var merged = exporter.merge(base, filled);

        assertEquals(2, merged.size());
        var featureFlag = merged.stream().filter(e -> e.config_key().equals("feature.flag")).findFirst().orElseThrow();
        assertEquals("enabled", featureFlag.config_value());
        // base entry unchanged.
        assertEquals("localhost", merged.stream().filter(e -> e.config_key().equals("db.host")).findFirst().orElseThrow().config_value());
    }

    @Test
    void missingListUsesSameSchemaAsEntries() {
        var inventory = new ConfigInventory(
            null, null, null,
            List.of(read("orphan.key", null)),
            List.of(), List.of(), List.of()
        );

        var result = exporter.export(inventory);
        var missingEntry = result.missing().getFirst();

        // Same fields present so a filled missing file can be merged directly.
        assertEquals(AppConfigCenterExporter.DEFAULT_SCOPE, missingEntry.scope());
        assertEquals("orphan", missingEntry.group_name());
        assertEquals("", missingEntry.config_value());
        assertEquals(0, missingEntry.secret());
    }

    // --- helpers ----------------------------------------------------------------

    private static String group(AppConfigCenterExporter.ExportResult result, String key) {
        return result.entries().stream()
            .filter(e -> e.config_key().equals(key))
            .map(AppConfigEntry::group_name)
            .findFirst().orElseThrow();
    }

    private static AppConfigEntry entry(String key, String value) {
        return new AppConfigEntry(
            AppConfigCenterExporter.DEFAULT_SCOPE, "default", key, value, 0, null, null, null, null
        );
    }

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, null, null, List.of(items), List.of(), List.of(), List.of());
    }

    private static ConfigFinding define(String key, String value, String path, String profile, SourceKind kind) {
        return new ConfigFinding(
            key, key, FindingRole.DEFINE,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            new EnvironmentContext(profile, null, null),
            new SourceLocation(path, 1, null, kind, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new io.github.hzzzzzx.configradar.core.model.UnknownDetails("test", key)
        );
    }

    private static ConfigFinding read(String key, ConfigValue value) {
        return new ConfigFinding(
            key, key, FindingRole.READ, value, null,
            EnvironmentContext.none(),
            new SourceLocation("App.java", 1, "App", SourceKind.JAVA, Scope.MAIN),
            Confidence.HIGH, "test",
            new io.github.hzzzzzx.configradar.core.model.UnknownDetails("test", key)
        );
    }

    private static ConfigFinding readWithDefault(String key, String defaultValue) {
        return new ConfigFinding(
            key, key, FindingRole.READ, null,
            new ConfigValue(defaultValue, defaultValue, ValueType.STRING),
            EnvironmentContext.none(),
            new SourceLocation("App.java", 1, "App", SourceKind.JAVA, Scope.MAIN),
            Confidence.HIGH, "test",
            new io.github.hzzzzzx.configradar.core.model.UnknownDetails("test", key)
        );
    }
}
