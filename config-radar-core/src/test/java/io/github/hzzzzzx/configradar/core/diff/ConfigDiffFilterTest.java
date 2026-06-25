package io.github.hzzzzzx.configradar.core.diff;

import io.github.hzzzzzx.configradar.core.model.ConfigChange;
import io.github.hzzzzzx.configradar.core.model.ConfigDiff;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ConfigDiffFilterTest {
    private final ConfigDiffFilter filter = new ConfigDiffFilter();

    @Test
    void keepsAddedRemovedTouchingChangedFilesOnly() {
        var diff = new ConfigDiff(
            null, null,
            List.of(finding("db.host", "src/main/resources/application.yml"),
                    finding("cache.ttl", "src/main/resources/cache.yml")),
            List.of(finding("old.key", "src/main/resources/application.yml")),
            List.of(), List.of(), List.of()
        );
        // only application.yml changed; cache.yml did not
        var changedFiles = Set.of("src/main/resources/application.yml");

        var filtered = filter.filter(diff, changedFiles, inventory(finding("db.host", "src/main/resources/application.yml")));

        assertEquals(1, filtered.added().size());
        assertEquals("db.host", filtered.added().getFirst().key());
        assertEquals(1, filtered.removed().size());
        assertEquals("old.key", filtered.removed().getFirst().key());
        assertEquals(1, filtered.summary().added());
        assertEquals(1, filtered.summary().removed());
    }

    @Test
    void resolvesChangedEntriesViaHeadInventory() {
        // ConfigChange has no source path; it must be resolved from the head inventory by key.
        var diff = new ConfigDiff(
            null, null,
            List.of(), List.of(),
            List.of(
                new ConfigChange("db.host", "value", "localhost", "prod-host"),   // in application.yml (changed)
                new ConfigChange("cache.ttl", "value", "10", "20")                // in cache.yml (not changed)
            ),
            List.of(), List.of()
        );
        var head = inventory(
            finding("db.host", "src/main/resources/application.yml"),
            finding("cache.ttl", "src/main/resources/cache.yml")
        );
        var changedFiles = Set.of("src/main/resources/application.yml");

        var filtered = filter.filter(diff, changedFiles, head);

        assertEquals(1, filtered.changed().size());
        assertEquals("db.host", filtered.changed().getFirst().key());
        assertEquals(1, filtered.summary().changed());
    }

    @Test
    void normalizesBackslashPathsOnWindows() {
        var diff = new ConfigDiff(
            null, null,
            List.of(finding("db.host", "src\\main\\resources\\application.yml")),
            List.of(), List.of(), List.of(), List.of()
        );
        var changedFiles = Set.of("src/main/resources/application.yml");

        var filtered = filter.filter(diff, changedFiles, inventory(finding("db.host", "src/main/resources/application.yml")));

        assertEquals(1, filtered.added().size(), "backslash source path matches forward-slash changed file");
    }

    private static ConfigInventory inventory(ConfigFinding... items) {
        return new ConfigInventory(null, null, null, List.of(items), List.of(), List.of(), List.of());
    }

    private static ConfigFinding finding(String key, String path) {
        return new ConfigFinding(
            key, key, FindingRole.DEFINE,
            new ConfigValue("v", "v", ValueType.STRING),
            null,
            EnvironmentContext.none(),
            new SourceLocation(path, 1, null, SourceKind.YAML, Scope.MAIN),
            Confidence.HIGH, "test",
            new io.github.hzzzzzx.configradar.core.model.UnknownDetails("test", key)
        );
    }
}
