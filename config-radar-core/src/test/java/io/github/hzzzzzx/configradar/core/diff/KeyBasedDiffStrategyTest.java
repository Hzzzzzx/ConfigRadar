package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class KeyBasedDiffStrategyTest {
    @Test
    void detectsAddedRemovedAndChangedItems() {
        var base = inventory(
            item("server.port", "8080", null),
            item("removed.key", "x", null),
            item("payment.timeout", "30", "dev")
        );
        var head = inventory(
            item("server.port", "9090", null),
            item("added.key", "y", null),
            item("payment.timeout", "30", "prod")
        );

        var diff = new KeyBasedDiffStrategy().diff(base, head);

        assertEquals("key", new KeyBasedDiffStrategy().id());
        assertTrue(diff.added().stream().anyMatch(item -> item.key().equals("added.key")));
        assertTrue(diff.added().stream().anyMatch(item -> item.key().equals("payment.timeout")));
        assertTrue(diff.removed().stream().anyMatch(item -> item.key().equals("removed.key")));
        assertTrue(diff.removed().stream().anyMatch(item -> item.key().equals("payment.timeout")));
        assertTrue(diff.changed().stream().anyMatch(change ->
            change.key().equals("server.port")
                && change.field().equals("value")
                && change.oldValue().equals("8080")
                && change.newValue().equals("9090")
        ));
        assertEquals(2, diff.summary().added());
        assertEquals(2, diff.summary().removed());
        assertEquals(1, diff.summary().changed());
    }

    @Test
    void matchesByNormalizedKeyInsteadOfRawKey() {
        var base = inventory(new ConfigFinding(
            "paymentTimeout",
            "payment-timeout",
            FindingRole.DEFINE,
            new ConfigValue("30", "30", ValueType.STRING),
            null,
            EnvironmentContext.none(),
            new SourceLocation("application.yml", 1, null, SourceKind.YAML, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", "paymentTimeout")
        ));
        var head = inventory(item("payment-timeout", "45", null));

        var diff = new KeyBasedDiffStrategy().diff(base, head);

        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
        assertEquals(1, diff.changed().size());
        assertEquals("payment-timeout", diff.changed().getFirst().key());
    }

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, null, null, List.of(items), List.of(), List.of(), List.of());
    }

    private static ConfigFinding item(String key, String value, String profile) {
        return new ConfigFinding(
            key,
            key,
            FindingRole.DEFINE,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            new EnvironmentContext(profile, null, null),
            new SourceLocation("application.yml", 1, null, SourceKind.YAML, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", key)
        );
    }
}
