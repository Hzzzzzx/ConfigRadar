package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainReason;
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

    @Test
    void fillsMissingEnvironmentFromHints() {
        var source = new SourceLocation("App.java", 1, null, SourceKind.JAVA, Scope.MAIN);
        var finding = new ConfigFinding(
            "server.port",
            "server.port",
            FindingRole.READ,
            null,
            null,
            new EnvironmentContext("prod", null, null),
            source,
            Confidence.HIGH,
            "test",
            new UnknownDetails("test", "")
        );
        var uncertain = new UncertainFinding(
            "prefix + key",
            UncertainReason.STRING_CONCAT,
            "Environment.getProperty",
            EnvironmentContext.none(),
            source,
            Confidence.LOW,
            "test",
            null
        );
        var input = new ScanInput(
            Path.of("."),
            List.of(),
            List.of(),
            null,
            new EnvironmentHints("dev", "cn", "blue"),
            null
        );

        var normalized = new BasicFindingNormalizer().normalize(
            List.of(finding, uncertain),
            new ScanContext(input, ScanOptions.defaults(), ConfigRules.empty(), new FileIndex(List.of()))
        );

        var config = normalized.stream()
            .filter(ConfigFinding.class::isInstance)
            .map(ConfigFinding.class::cast)
            .findFirst()
            .orElseThrow();
        assertEquals("prod", config.environment().profile());
        assertEquals("cn", config.environment().region());
        assertEquals("blue", config.environment().namespace());

        var dynamic = normalized.stream()
            .filter(UncertainFinding.class::isInstance)
            .map(UncertainFinding.class::cast)
            .findFirst()
            .orElseThrow();
        assertEquals("dev", dynamic.environment().profile());
        assertEquals("cn", dynamic.environment().region());
        assertEquals("blue", dynamic.environment().namespace());
    }
}
