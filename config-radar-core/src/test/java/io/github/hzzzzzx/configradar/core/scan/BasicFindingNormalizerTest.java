package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BasicFindingNormalizerTest {
    @Test
    void normalizesCamelUnderscoreAndCaseWithoutChangingRawKey() {
        var finding = new ConfigFinding(
            "paymentTimeout",
            "paymentTimeout",
            FindingRole.READ,
            null,
            null,
            EnvironmentContext.none(),
            new SourceLocation("App.java", 1, null, SourceKind.JAVA, Scope.MAIN),
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", "")
        );

        var normalized = new BasicFindingNormalizer().normalize(
            List.of(finding),
            new ScanContext(ScanInput.of(Path.of(".")), ScanOptions.defaults(), ConfigRules.empty(), new FileIndex(List.of()))
        ).stream().map(ConfigFinding.class::cast).findFirst().orElseThrow();

        assertEquals("paymentTimeout", normalized.key());
        assertEquals("payment-timeout", normalized.normalizedKey());
        assertEquals("server-port", BasicFindingNormalizer.normalizeKey("SERVER_PORT"));
    }
}
