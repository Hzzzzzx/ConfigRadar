package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigCenterDetails;
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

    @Test
    void treatsRegionAndNamespaceAsIdentityDimensions() {
        var base = inventory(item("server.port", "8080", "prod", "us", "blue"));
        var head = inventory(item("server.port", "8080", "prod", "eu", "blue"));

        var diff = new KeyBasedDiffStrategy().diff(base, head);

        assertEquals(1, diff.added().size());
        assertEquals(1, diff.removed().size());
        assertTrue(diff.changed().isEmpty());
    }

    @Test
    void detectsValueTypeChanges() {
        var base = inventory(typedItem("feature.enabled", "true", ValueType.STRING));
        var head = inventory(typedItem("feature.enabled", "true", ValueType.BOOLEAN));

        var diff = new KeyBasedDiffStrategy().diff(base, head);

        assertTrue(diff.changed().stream().anyMatch(change ->
            change.key().equals("feature.enabled")
                && change.field().equals("value.type")
                && change.oldValue().equals("STRING")
                && change.newValue().equals("BOOLEAN")
        ));
    }

    @Test
    void addsCheckForNewUncertainFinding() {
        var base = inventory();
        var head = new ConfigInventory(
            null,
            null,
            null,
            List.of(),
            List.of(uncertain("environment.getProperty(prefix + \".url\")")),
            List.of(),
            List.of()
        );

        var diff = new KeyBasedDiffStrategy().diff(base, head);

        assertEquals(1, diff.uncertainChanged().size());
        assertEquals(1, diff.checks().size());
        assertEquals(1, diff.summary().checks());
        assertEquals("dynamic-config-key", diff.checks().getFirst().type());
    }

    @Test
    void addsCheckForNewRemoteConfigFinding() {
        var diff = new KeyBasedDiffStrategy().diff(
            inventory(),
            inventory(configCenterItem("apollo.app.timeout"))
        );

        assertEquals(1, diff.checks().size());
        assertEquals(1, diff.summary().checks());
        assertEquals("remote-config-source", diff.checks().getFirst().type());
        assertEquals("apollo.app.timeout", diff.checks().getFirst().key());
    }

    @Test
    void addsCheckForNewSensitiveLookingKey() {
        var diff = new KeyBasedDiffStrategy().diff(
            inventory(),
            inventory(item("redis.password", "secret", null))
        );

        assertEquals(1, diff.checks().size());
        assertEquals(1, diff.summary().checks());
        assertEquals("sensitive-looking-key", diff.checks().getFirst().type());
        assertEquals("redis.password", diff.checks().getFirst().key());
    }

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, null, null, List.of(items), List.of(), List.of(), List.of());
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

    private static ConfigFinding item(String key, String value, String profile) {
        return item(key, value, profile, null, null);
    }

    private static ConfigFinding item(String key, String value, String profile, String region, String namespace) {
        return item(key, value, ValueType.STRING, profile, region, namespace);
    }

    private static ConfigFinding typedItem(String key, String value, ValueType type) {
        return item(key, value, type, null, null, null);
    }

    private static ConfigFinding configCenterItem(String key) {
        return new ConfigFinding(
            key,
            key,
            FindingRole.READ,
            null,
            new ConfigValue("3000", "3000", ValueType.INTEGER),
            EnvironmentContext.none(),
            new SourceLocation("App.java", 1, "App", SourceKind.JAVA, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new ConfigCenterDetails("application", null, null, "3000")
        );
    }

    private static ConfigFinding item(
        String key,
        String value,
        ValueType type,
        String profile,
        String region,
        String namespace
    ) {
        return new ConfigFinding(
            key,
            key,
            FindingRole.DEFINE,
            new ConfigValue(value, value, type),
            null,
            new EnvironmentContext(profile, region, namespace),
            new SourceLocation("application.yml", 1, null, SourceKind.YAML, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", key)
        );
    }
}
