package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import io.github.hzzzzzx.configradar.core.semantic.CodeConfigUsage;
import io.github.hzzzzzx.configradar.core.semantic.CodeSemanticProvider;
import io.github.hzzzzzx.configradar.core.semantic.UsageKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CodegraphConfigUsageDetectorTest {
    @Test
    void convertsSemanticProviderUsagesToReadFindings() throws Exception {
        var detector = new CodegraphConfigUsageDetector(new FakeProvider());
        var context = new ScanContext(
            ScanInput.of(Path.of(".")),
            ScanOptions.defaults(),
            ConfigRules.empty(),
            new FileIndex(List.of())
        );

        var finding = detector.detect(context).stream()
            .map(ConfigFinding.class::cast)
            .findFirst()
            .orElseThrow();

        assertEquals("payment.timeout", finding.key());
        assertEquals("payment.timeout", finding.normalizedKey());
        assertEquals("codegraph-config-usage", finding.detectorId());
        assertEquals(SourceKind.JAVA, finding.source().sourceKind());
    }

    private static final class FakeProvider implements CodeSemanticProvider {
        @Override
        public boolean available(Path projectRoot) {
            return true;
        }

        @Override
        public List<CodeConfigUsage> findConfigUsages(ScanContext context) {
            return List.of(new CodeConfigUsage(
                "payment.timeout",
                UsageKind.CUSTOM_ANNOTATION,
                new SourceLocation("src/main/java/App.java", 7, null, SourceKind.JAVA, Scope.MAIN),
                Confidence.MEDIUM,
                new ExternalDetails("codegraph", "custom-meta-annotation:PaymentTimeout", null)
            ));
        }
    }
}
