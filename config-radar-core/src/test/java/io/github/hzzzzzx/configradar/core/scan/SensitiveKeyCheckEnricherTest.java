package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ProjectInfo;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SensitiveKeyCheckEnricherTest {
    @Test
    void addsWarningForSensitiveLookingKey() {
        var source = new SourceLocation("application.yml", 1, null, SourceKind.YAML, Scope.MAIN);
        var inventory = new ConfigInventory(
            null,
            ProjectInfo.unknown(),
            null,
            List.of(item("redis.password", source)),
            List.of(),
            List.of(),
            List.of()
        );

        var enriched = new SensitiveKeyCheckEnricher().enrich(inventory, null);

        assertEquals(1, enriched.checks().size());
        assertEquals("sensitive-looking-key", enriched.checks().getFirst().type());
        assertEquals(DiagnosticSeverity.WARNING, enriched.checks().getFirst().severity());
        assertEquals("redis.password", enriched.checks().getFirst().key());
    }

    private static ConfigFinding item(String key, SourceLocation source) {
        return new ConfigFinding(
            key,
            key,
            FindingRole.DEFINE,
            new ConfigValue("secret", "secret", ValueType.STRING),
            null,
            EnvironmentContext.none(),
            source,
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", key)
        );
    }
}
