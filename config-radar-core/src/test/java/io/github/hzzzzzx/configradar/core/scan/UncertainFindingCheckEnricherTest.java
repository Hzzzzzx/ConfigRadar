package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.ProjectInfo;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainReason;
import io.github.hzzzzzx.configradar.core.model.UnknownUncertainDetails;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class UncertainFindingCheckEnricherTest {
    @Test
    void addsErrorCheckForUncertainFinding() {
        var source = new SourceLocation("src/main/java/App.java", 7, "App", SourceKind.JAVA, Scope.MAIN);
        var inventory = new ConfigInventory(
            null,
            ProjectInfo.unknown(),
            null,
            List.of(),
            List.of(new UncertainFinding(
                "environment.getProperty(prefix + \".url\")",
                UncertainReason.STRING_CONCAT,
                "Environment.getProperty",
                null,
                source,
                Confidence.LOW,
                "test",
                new UnknownUncertainDetails("dynamic")
            )),
            List.of(),
            List.of()
        );

        var enriched = new UncertainFindingCheckEnricher().enrich(inventory, null);

        assertEquals(1, enriched.checks().size());
        assertEquals("dynamic-config-key", enriched.checks().getFirst().type());
        assertEquals(DiagnosticSeverity.ERROR, enriched.checks().getFirst().severity());
        assertEquals(source, enriched.checks().getFirst().source());
    }
}
