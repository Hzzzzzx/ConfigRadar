package io.github.hzzzzzx.configradar.core.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ConfigInventoryTest {
    @Test
    void defaultsMetadataAndProtectsSectionsFromMutation() {
        var source = new SourceLocation("src/main/java/App.java", 5, "App", SourceKind.JAVA, Scope.MAIN);
        var finding = new ConfigFinding(
            "server.port",
            "server.port",
            FindingRole.DEFINE,
            new ConfigValue("8080", "8080", ValueType.INTEGER),
            null,
            null,
            source,
            Confidence.HIGH,
            "test",
            new JavaSystemPropertyDetails(null, false)
        );
        var uncertain = new UncertainFinding(
            "env.getProperty(prefix + \".port\")",
            UncertainReason.STRING_CONCAT,
            "Environment.getProperty",
            null,
            source,
            Confidence.LOW,
            "test",
            new UnknownUncertainDetails("env.getProperty(prefix + \".port\")")
        );
        var items = new ArrayList<>(List.of(finding));
        var uncertainItems = new ArrayList<>(List.of(uncertain));

        var inventory = new ConfigInventory(null, null, null, items, uncertainItems, null, null);
        items.clear();
        uncertainItems.clear();

        assertEquals(ConfigInventory.SCHEMA_VERSION, inventory.schemaVersion());
        assertEquals(ProjectInfo.unknown(), inventory.project());
        assertEquals(1, inventory.summary().findings());
        assertEquals(1, inventory.summary().uncertain());
        assertEquals(List.of(finding), inventory.items());
        assertEquals(List.of(uncertain), inventory.uncertain());
        assertEquals(List.of(finding, uncertain), inventory.allFindings());
        assertThrows(UnsupportedOperationException.class, () -> inventory.items().add(finding));
        assertThrows(UnsupportedOperationException.class, () -> inventory.allFindings().add(finding));
    }

    @Test
    void withSummaryKeepsInventorySections() {
        var inventory = new ConfigInventory(null, null, null, null, null, null, null);
        var summary = new InventorySummary(2, 3, 4, 5, 6);

        var updated = inventory.withSummary(summary);

        assertEquals(summary, updated.summary());
        assertEquals(inventory.schemaVersion(), updated.schemaVersion());
        assertEquals(inventory.project(), updated.project());
        assertEquals(inventory.items(), updated.items());
        assertEquals(inventory.uncertain(), updated.uncertain());
    }
}
