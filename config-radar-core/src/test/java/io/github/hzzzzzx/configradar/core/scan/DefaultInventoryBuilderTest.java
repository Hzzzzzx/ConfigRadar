package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.JavaSystemPropertyDetails;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainReason;
import io.github.hzzzzzx.configradar.core.model.UnknownUncertainDetails;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DefaultInventoryBuilderTest {
    @Test
    void splitsConfirmedAndUncertainFindingsIntoInventorySections() throws Exception {
        var source = new SourceLocation("src/main/java/App.java", 12, "App", SourceKind.JAVA, Scope.MAIN);
        var confirmed = new ConfigFinding(
            "payment.timeout",
            "payment.timeout",
            FindingRole.READ,
            null,
            null,
            null,
            source,
            Confidence.HIGH,
            "test-detector",
            new JavaSystemPropertyDetails(null, false)
        );
        var uncertain = new UncertainFinding(
            "environment.getProperty(prefix + \".timeout\")",
            UncertainReason.STRING_CONCAT,
            "Environment.getProperty",
            null,
            source,
            Confidence.LOW,
            "test-detector",
            new UnknownUncertainDetails("environment.getProperty(prefix + \".timeout\")")
        );

        var input = ScanInput.of(FixturePaths.springBasic());
        var index = new DefaultFileIndexer().index(input, ScanOptions.defaults());
        var context = new ScanContext(input, ScanOptions.defaults(), ConfigRules.empty(), index);
        var inventory = new DefaultInventoryBuilder().build(List.of(confirmed, uncertain), context);

        assertEquals("spring-basic", inventory.project().name());
        assertEquals(List.of(confirmed), inventory.items());
        assertEquals(List.of(uncertain), inventory.uncertain());
        assertTrue(inventory.checks().isEmpty());
        assertTrue(inventory.diagnostics().isEmpty());
        assertEquals(1, inventory.summary().keys());
        assertEquals(1, inventory.summary().findings());
        assertEquals(1, inventory.summary().uncertain());
    }
}
