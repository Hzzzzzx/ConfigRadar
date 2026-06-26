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
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XacDiffConsumerTest {

    @Test
    void routesAddedAndChangedIntoUpsertManifestPartitionedBySensitivity() throws Exception {
        var diff = new ConfigDiff(
            null, null,
            // added: one normal, one sensitive
            List.of(
                finding("server.port", "8080"),
                finding("db.password", "secret123")
            ),
            List.of(),
            // changed: only the "value" field carries a publishable new value
            List.of(
                new ConfigChange("cache.ttl", "value", "10", "20", "application.yml"),
                new ConfigChange("cache.ttl", "value.type", "STRING", "INTEGER", "application.yml")
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

        // normal added + changed-value keys land in app_configs
        @SuppressWarnings("unchecked")
        var appConfigs = (List<Map<String, Object>>) data.get("app_configs");
        assertEquals(2, appConfigs.size());
        var keys = appConfigs.stream().map(e -> e.get("config_key")).toList();
        assertTrue(keys.contains("cache.ttl"), "changed value key should be upserted");
        assertTrue(keys.contains("server.port"), "added key should be upserted");
        // a key whose change is only value.type must NOT appear (no publishable value)
        // (server.port is added, not changed — so no duplicate from value.type here)

        // sensitive added key lands in J2C.secrets
        @SuppressWarnings("unchecked")
        var secrets = (List<Map<String, Object>>) ((Map<String, Object>) data.get("J2C")).get("secrets");
        assertEquals(1, secrets.size());
        assertEquals("db_password", secrets.getFirst().get("key"));
    }

    @Test
    void writesRemovedKeysAsAPlainList() throws Exception {
        var diff = new ConfigDiff(
            null, null,
            List.of(),
            List.of(
                finding("db.password", "secret"),
                finding("cache.ttl", "10")
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
    void changedValueFieldWithMultipleChangesUpsertsKeyOnce() throws Exception {
        // a single changed key with multiple field changes must contribute exactly one upsert entry
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

    // --- helpers ---

    private static ConfigFinding finding(String key, String value) {
        return new ConfigFinding(
            key, key, FindingRole.DEFINE,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            new EnvironmentContext(null, null, null),
            new SourceLocation("application.yml", 1, null, SourceKind.YAML, Scope.MAIN),
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
