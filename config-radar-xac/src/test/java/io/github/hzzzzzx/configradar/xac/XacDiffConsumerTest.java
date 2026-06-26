package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigChange;
import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XacDiffConsumerTest {

    @Test
    void valueBearingAddedAndChangedGoToStrictManifestPartitionedBySensitivity() throws Exception {
        var diff = new ConfigDiff(
            null, null,
            // added: one normal value-bearing, one sensitive value-bearing
            List.of(
                finding("server.port", "8080", "src/app/application.yml"),
                finding("db.password", "secret123", "src/app/application.yml")
            ),
            List.of(),
            // changed: only the "value" field carries a publishable new value
            List.of(
                new ConfigChange("cache.ttl", "value", "10", "20", "src/app/application.yml"),
                new ConfigChange("cache.ttl", "value.type", "STRING", "INTEGER", "src/app/application.yml")
            ),
            List.of(), List.of()
        );

        var sink = new MemorySink();
        new XacDiffConsumer().consume(diff, ConsumerContext.of("prod", null, null), sink);

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("app-configs-changed.yaml").toByteArray(), Map.class);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) root.get("data");

        // value-bearing added + changed-value keys land in app_configs
        @SuppressWarnings("unchecked")
        var appConfigs = (List<Map<String, Object>>) data.get("app_configs");
        assertEquals(2, appConfigs.size());
        var keys = appConfigs.stream().map(e -> e.get("config_key")).toList();
        assertTrue(keys.contains("cache.ttl"), "changed value key should be in the strict manifest");
        assertTrue(keys.contains("server.port"), "value-bearing added key should be in the strict manifest");

        // sensitive added key lands in J2C.secrets
        @SuppressWarnings("unchecked")
        var secrets = (List<Map<String, Object>>) ((Map<String, Object>) data.get("J2C")).get("secrets");
        assertEquals(1, secrets.size());
        assertEquals("db_password", secrets.getFirst().get("key"));

        // nothing valueless in this diff -> no missing file
        assertNull(sink.get("app-configs-missing.yaml"), "no valueless keys -> no missing file");
    }

    @Test
    void valuelessAddedAndChangedGoToMissingListWithSource() throws Exception {
        var diff = new ConfigDiff(
            null, null,
            // added: a valueless key (read in code, never defined, no value)
            List.of(readFinding("dynamic.key", "src/main/java/App.java")),
            List.of(),
            // changed: a key whose new value is empty
            List.of(
                new ConfigChange("emptied.key", "value", "old", "", "src/app/application.yml")
            ),
            List.of(), List.of()
        );

        var sink = new MemorySink();
        new XacDiffConsumer().consume(diff, ConsumerContext.of("prod", null, null), sink);

        // valueless keys -> no strict manifest
        assertNull(sink.get("app-configs-changed.yaml"), "valueless keys -> no strict manifest");

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("app-configs-missing.yaml").toByteArray(), Map.class);
        @SuppressWarnings("unchecked")
        var missing = (List<Map<String, Object>>) root.get("missing");
        assertEquals(2, missing.size());

        var byKey = missing.stream().collect(java.util.stream.Collectors.toMap(e -> (String) e.get("config_key"), e -> e));
        // valueless added carries its source path
        assertEquals("src/main/java/App.java", byKey.get("dynamic.key").get("source"));
        assertEquals("added without value", byKey.get("dynamic.key").get("reason"));
        // emptied changed carries its newSource
        assertEquals("src/app/application.yml", byKey.get("emptied.key").get("source"));
        assertEquals("changed to empty value", byKey.get("emptied.key").get("reason"));
    }

    @Test
    void writesRemovedKeysAsAPlainListWithGroup() throws Exception {
        var diff = new ConfigDiff(
            null, null,
            List.of(),
            List.of(
                finding("db.password", "secret", "src/app/application.yml"),
                finding("cache.ttl", "10", "src/app/application.yml")
            ),
            List.of(),
            List.of(), List.of()
        );

        var sink = new MemorySink();
        new XacDiffConsumer().consume(diff, ConsumerContext.of("prod", null, null), sink);

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("removed.yaml").toByteArray(), Map.class);
        @SuppressWarnings("unchecked")
        var removed = (List<Map<String, Object>>) root.get("removed");
        assertEquals(2, removed.size());
        assertEquals("cache.ttl", removed.get(0).get("config_key"));
        assertEquals("cache", removed.get(0).get("group_name"));
        assertEquals("db.password", removed.get(1).get("config_key"));
    }

    @Test
    void changedValueFieldWithMultipleChangesContributesOneEntry() throws Exception {
        // a single changed key with multiple field changes must contribute exactly one entry
        var diff = new ConfigDiff(
            null, null,
            List.of(),
            List.of(),
            List.of(
                new ConfigChange("server.port", "value", "8080", "9090", "application.yml"),
                new ConfigChange("server.port", "defaultValue", "8080", "9090", "application.yml")
            ),
            List.of(), List.of()
        );

        var sink = new MemorySink();
        new XacDiffConsumer().consume(diff, ConsumerContext.of("prod", null, null), sink);

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("app-configs-changed.yaml").toByteArray(), Map.class);
        @SuppressWarnings("unchecked")
        var appConfigs = (List<Map<String, Object>>) ((Map<String, Object>) root.get("data")).get("app_configs");
        assertEquals(1, appConfigs.size());
        assertEquals("9090", appConfigs.getFirst().get("config_value"));
    }

    @Test
    void emptyDiffSkipsAllOptionalFiles() throws Exception {
        // no added/changed/removed -> none of the three files are written
        var diff = new ConfigDiff(null, null, List.of(), List.of(), List.of(), List.of(), List.of());

        var sink = new MemorySink();
        new XacDiffConsumer().consume(diff, ConsumerContext.of("prod", null, null), sink);

        assertNull(sink.get("app-configs-changed.yaml"));
        assertNull(sink.get("app-configs-missing.yaml"));
        assertNull(sink.get("removed.yaml"));
    }

    // --- helpers ---

    /** A DEFINE finding with a value (value-bearing). */
    private static ConfigFinding finding(String key, String value, String path) {
        return new ConfigFinding(
            key, key, FindingRole.DEFINE,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            new EnvironmentContext(null, null, null),
            new SourceLocation(path, 1, null, SourceKind.YAML, Scope.MAIN),
            Confidence.HIGH, "test",
            new io.github.hzzzzzx.configradar.core.model.UnknownDetails("test", key)
        );
    }

    /** A READ finding with no value (read in code but never defined) — valueless. */
    private static ConfigFinding readFinding(String key, String path) {
        return new ConfigFinding(
            key, key, FindingRole.READ, null, null,
            EnvironmentContext.none(),
            new SourceLocation(path, 1, "App", SourceKind.JAVA, Scope.MAIN),
            Confidence.HIGH, "test",
            new io.github.hzzzzzx.configradar.core.model.UnknownDetails("test", key)
        );
    }

    static final class MemorySink implements io.github.hzzzzzx.configradar.core.output.ConsumerSink {
        private final Map<String, ByteArrayOutputStream> contents = new java.util.LinkedHashMap<>();

        @Override
        public java.io.OutputStream openFile(String fileName) {
            var bytes = new ByteArrayOutputStream();
            contents.put(fileName, bytes);
            return bytes;
        }

        ByteArrayOutputStream get(String name) {
            return contents.get(name);
        }
    }
}
