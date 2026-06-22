package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.InventoryCheck;
import io.github.hzzzzzx.configradar.core.model.JavaSystemPropertyDetails;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.pack.DetectorPack;
import io.github.hzzzzzx.configradar.core.rule.ConfigRule;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScanPipelineBuilderTest {
    @Test
    void builderComposesPipelineStagesInOrder() throws Exception {
        var source = new SourceLocation("src/main/java/App.java", 7, "App", SourceKind.JAVA, Scope.MAIN);
        var finding = new ConfigFinding(
            "SERVER_PORT",
            "SERVER_PORT",
            FindingRole.READ,
            null,
            null,
            null,
            source,
            Confidence.HIGH,
            "builder-test",
            new JavaSystemPropertyDetails(null, false)
        );

        var pipeline = new ScanPipelineBuilder()
            .detector(new ConfigDetector() {
                @Override
                public String id() {
                    return "builder-test";
                }

                @Override
                public List<ScanFinding> detect(ScanContext context) {
                    return List.of(finding);
                }
            })
            .detectorPack(new DetectorPack() {
                @Override
                public String id() {
                    return "builder-pack";
                }

                @Override
                public List<ConfigDetector> detectors() {
                    return List.of(new ConfigDetector() {
                        @Override
                        public String id() {
                            return "builder-pack-detector";
                        }

                        @Override
                        public List<ScanFinding> detect(ScanContext context) {
                            return List.of(new ConfigFinding(
                                "PACK_KEY",
                                "PACK_KEY",
                                FindingRole.READ,
                                null,
                                null,
                                null,
                                source,
                                Confidence.HIGH,
                                "builder-pack-detector",
                                new JavaSystemPropertyDetails(null, false)
                            ));
                        }
                    });
                }

                @Override
                public List<ConfigRule> rules() {
                    return List.of();
                }
            })
            .processor(new FindingProcessor() {
                @Override
                public String id() {
                    return "keep-main-only";
                }

                @Override
                public List<ScanFinding> process(List<ScanFinding> findings, ScanContext context) {
                    return context.options().includeTests() ? List.of() : findings;
                }
            })
            .normalizer(new BasicFindingNormalizer())
            .enricher(new InventoryEnricher() {
                @Override
                public String id() {
                    return "builder-check";
                }

                @Override
                public ConfigInventory enrich(ConfigInventory inventory, ScanContext context) {
                    var checks = new ArrayList<>(inventory.checks());
                    checks.add(new InventoryCheck("builder-check", DiagnosticSeverity.INFO, "ok", "server-port", source));
                    return new ConfigInventory(
                        inventory.schemaVersion(),
                        inventory.project(),
                        inventory.summary(),
                        inventory.items(),
                        inventory.uncertain(),
                        checks,
                        inventory.diagnostics()
                    );
                }
            })
            .enricher(new SummaryInventoryEnricher())
            .build();

        var result = pipeline.scan(ScanInput.of(FixturePaths.springBasic()), ScanOptions.defaults(), ConfigRules.empty());

        assertTrue(result.inventory().items().stream().anyMatch(item -> item.normalizedKey().equals("server-port")));
        assertTrue(result.inventory().items().stream().anyMatch(item -> item.normalizedKey().equals("pack-key")));
        assertEquals(1, result.inventory().checks().size());
        assertEquals(1, result.inventory().summary().checks());
    }
}
