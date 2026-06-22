package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ProjectInfo;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SensitiveValueRedactionEnricherTest {
    @Test
    void masksSensitiveValuesWhenEnabled() {
        var inventory = inventory(item("redis.password", "redis-secret"), item("server.port", "8080"));
        var context = new ScanContext(
            ScanInput.of(Path.of(".")),
            new ScanOptions(false, true, 0, 0, null, RedactionPolicy.redactSensitive()),
            ConfigRules.empty(),
            new FileIndex(List.of())
        );

        var enriched = new SensitiveValueRedactionEnricher().enrich(inventory, context);

        assertEquals("******", enriched.items().get(0).value().raw());
        assertEquals("8080", enriched.items().get(1).value().raw());
    }

    @Test
    void leavesValuesAloneWhenDisabled() {
        var inventory = inventory(item("redis.password", "redis-secret"));
        var context = new ScanContext(
            ScanInput.of(Path.of(".")),
            ScanOptions.defaults(),
            ConfigRules.empty(),
            new FileIndex(List.of())
        );

        var enriched = new SensitiveValueRedactionEnricher().enrich(inventory, context);

        assertEquals("redis-secret", enriched.items().getFirst().value().raw());
    }

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, ProjectInfo.unknown(), null, List.of(items), List.of(), List.of(), List.of());
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
