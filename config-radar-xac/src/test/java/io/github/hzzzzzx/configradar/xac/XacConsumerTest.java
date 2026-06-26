package io.github.hzzzzzx.configradar.xac;

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
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class XacConsumerTest {
    @TempDir
    Path tempDir;

    @Test
    void sensitiveKeyRoutesToJ2cSecretsUnderData() throws Exception {
        var inventory = inventory(
            define("db.password", "secret123", "src/main/resources/application.yml")
        );
        var sink = new MemorySink();

        new XacConsumer().consume(inventory, ConsumerContext.of("prod", null, null), sink);

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("app-configs.yaml").toByteArray(), Map.class);
        // manifest shape
        assertEquals("com.huawei.his.appconfigcenter.v3", root.get("apiVersion"));
        assertEquals("his.appconfigcenter", root.get("kind"));
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) root.get("data");
        // sensitive key routed to J2C.secrets
        assertTrue(((List<?>) data.get("app_configs")).isEmpty(), "no app_configs for a sensitive-only inventory");
        @SuppressWarnings("unchecked")
        var secrets = (List<Map<String, Object>>) ((Map<String, Object>) data.get("J2C")).get("secrets");
        assertEquals(1, secrets.size());
        assertEquals("db_password", secrets.getFirst().get("key"));
        assertEquals("${db_password}", secrets.getFirst().get("password"));
    }

    @Test
    void resolvesScopePerProfileWhenMappingProvided() throws Exception {
        // two distinct keys with different profiles -> each entry gets its own scope via -D scope.<profile>
        // (same key across profiles would be deduplicated to one winner, so we use distinct keys here)
        var inventory = inventory(
            defineWithProfile("server.port", "8080", "prod"),
            defineWithProfile("cache.ttl", "30", "dev")
        );
        var ctx = new ConsumerContext(null, null, null, java.util.Map.of(
            "scope.prod", "obp-prod",
            "scope.dev", "obp-dev"
        ));
        var sink = new MemorySink();

        new XacConsumer().consume(inventory, ctx, sink);

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("app-configs.yaml").toByteArray(), Map.class);
        @SuppressWarnings("unchecked")
        var appConfigs = (List<Map<String, Object>>) ((Map<String, Object>) root.get("data")).get("app_configs");
        var byKey = appConfigs.stream().collect(java.util.stream.Collectors.toMap(
            e -> (String) e.get("config_key"), e -> (String) e.get("scope")));
        assertEquals("obp-prod", byKey.get("server.port"), "prod finding resolves to obp-prod");
        assertEquals("obp-dev", byKey.get("cache.ttl"), "dev finding resolves to obp-dev");
    }

    @Test
    void usesFallbackScopeWhenNoMappingProvided() throws Exception {
        var inventory = inventory(define("server.port", "8080", "src/main/resources/application.yml"));
        var sink = new MemorySink();

        new XacConsumer().consume(inventory, ConsumerContext.of("prod", null, null), sink);

        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) YamlSupport.mapper().readValue(
            sink.get("app-configs.yaml").toByteArray(), Map.class);
        @SuppressWarnings("unchecked")
        var appConfigs = (List<Map<String, Object>>) ((Map<String, Object>) root.get("data")).get("app_configs");
        assertEquals(io.github.hzzzzzx.configradar.xac.ScopeMapping.FALLBACK_SCOPE,
            appConfigs.getFirst().get("scope"), "no mapping -> placeholder scope");
    }

    // --- helpers ---

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, null, null, List.of(items), List.of(), List.of(), List.of());
    }

    private static ConfigFinding define(String key, String value, String path) {
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

    private static ConfigFinding defineWithProfile(String key, String value, String profile) {
        return new ConfigFinding(
            key, key, FindingRole.DEFINE,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            new EnvironmentContext(profile, null, null),
            new SourceLocation("src/main/resources/application-" + profile + ".yml", 1, null, SourceKind.YAML, Scope.MAIN),
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
        ByteArrayOutputStream get(String name) { return contents.get(name); }
    }
}
