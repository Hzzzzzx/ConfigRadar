package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EmptyDiffStrategyTest {
    @Test
    void returnsSchemaCompatibleEmptyDiff() {
        var diff = new EmptyDiffStrategy().diff(
            new ConfigInventory(null, null, null, null, null, null, null),
            new ConfigInventory(null, null, null, null, null, null, null)
        );

        assertEquals("empty", new EmptyDiffStrategy().id());
        assertEquals("config-diff/v1", diff.schemaVersion());
        assertTrue(diff.added().isEmpty());
        assertTrue(diff.removed().isEmpty());
        assertEquals(0, diff.summary().changed());
    }
}
