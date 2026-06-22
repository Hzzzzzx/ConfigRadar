package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.Diagnostic;
import io.github.hzzzzzx.configradar.core.model.DiagnosticSeverity;
import io.github.hzzzzzx.configradar.core.model.PerformanceMetric;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.rule.ConfigRules;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Composition root for the static scan skeleton. */
public final class ScanPipeline {
    private static final Logger LOG = Logger.getLogger(ScanPipeline.class.getName());

    private final FileIndexer fileIndexer;
    private final DetectorRegistry detectorRegistry;
    private final List<FindingProcessor> processors;
    private final List<FindingNormalizer> normalizers;
    private final InventoryBuilder inventoryBuilder;
    private final List<InventoryEnricher> enrichers;

    /**
     * Creates a scan pipeline from composable stages.
     *
     * @param fileIndexer classifies project files before detectors run
     * @param detectorRegistry detector list to execute
     * @param processors ordered processors for raw findings
     * @param normalizers ordered normalizers for processed findings
     * @param inventoryBuilder converts normalized findings to inventory output
     * @param enrichers ordered enrichers for summaries, checks, and risk data
     */
    public ScanPipeline(
        FileIndexer fileIndexer,
        DetectorRegistry detectorRegistry,
        List<FindingProcessor> processors,
        List<FindingNormalizer> normalizers,
        InventoryBuilder inventoryBuilder,
        List<InventoryEnricher> enrichers
    ) {
        this.fileIndexer = fileIndexer;
        this.detectorRegistry = detectorRegistry;
        this.processors = List.copyOf(processors == null ? List.of() : processors);
        this.normalizers = List.copyOf(normalizers == null ? List.of() : normalizers);
        this.inventoryBuilder = inventoryBuilder;
        this.enrichers = List.copyOf(enrichers == null ? List.of() : enrichers);
    }

    /**
     * Creates the default empty skeleton pipeline.
     *
     * <p>This pipeline indexes files, runs no real detectors, and still builds a valid empty
     * inventory. It is useful for proving the outer flow before detector logic exists.</p>
     *
     * @return runnable skeleton pipeline
     */
    public static ScanPipeline empty() {
        return new ScanPipelineBuilder()
            .processor(new NoopFindingProcessor())
            .normalizer(new NoopFindingNormalizer())
            .enricher(new SummaryInventoryEnricher())
            .build();
    }

    /**
     * Creates the default static-source pipeline with built-in detectors.
     *
     * @return runnable pipeline for the first real scanning path
     */
    public static ScanPipeline defaults() {
        return defaults(false);
    }

    /**
     * Creates the default static-source pipeline with optional external semantic detectors.
     *
     * @param enableCodegraph whether to add the opt-in codegraph-backed detector
     * @return runnable pipeline for the first real scanning path
     */
    public static ScanPipeline defaults(boolean enableCodegraph) {
        var detectors = new ArrayList<ConfigDetector>();
        detectors.add(new SpringConfigFileDetector());
        detectors.add(new SpringConfigurationMetadataDetector());
        detectors.add(new LogbackSpringXmlDetector());
        detectors.add(new JavaSourceConfigDetector());
        if (enableCodegraph) {
            detectors.add(new CodegraphConfigUsageDetector());
        }
        var builder = new ScanPipelineBuilder()
            .processor(new NoopFindingProcessor())
            .normalizer(new BasicFindingNormalizer())
            .enricher(new UncertainFindingCheckEnricher())
            .enricher(new SummaryInventoryEnricher());
        for (var detector : detectors) {
            builder.detector(detector);
        }
        return builder.build();
    }

    /**
     * Runs the full scan skeleton.
     *
     * <p>Detector failures are converted to diagnostics so one broken detector does not stop the
     * rest of the scan. Structural failures such as an unreadable project root are still thrown to
     * the caller.</p>
     *
     * @param input project path and optional input hints
     * @param options execution options such as parallelism and test inclusion
     * @param rules project-local declarative rules
     * @return inventory plus diagnostics and performance metrics
     * @throws Exception when required pipeline stages fail outside detector-level isolation
     */
    public ScanResult scan(ScanInput input, ScanOptions options, ConfigRules rules) throws Exception {
        var metrics = new ArrayList<PerformanceMetric>();
        LOG.info(() -> "Starting ConfigRadar scan: " + input.projectRoot());

        var startIndex = System.nanoTime();
        var index = fileIndexer.index(input, options);
        metrics.add(metric("file-indexing", startIndex));

        var context = new ScanContext(input, options, rules == null ? ConfigRules.empty() : rules, index);
        var diagnostics = new ArrayList<Diagnostic>();
        List<ScanFinding> findings = new ArrayList<>();

        var startDetectors = System.nanoTime();
        for (var detector : detectorRegistry.detectors()) {
            try {
                findings.addAll(detector.detect(context));
            } catch (Exception error) {
                diagnostics.add(new Diagnostic(
                    DiagnosticSeverity.WARNING,
                    "detector",
                    error.getMessage(),
                    detector.id()
                ));
            }
        }
        metrics.add(metric("detector-execution", startDetectors));

        var startProcessors = System.nanoTime();
        for (var processor : processors) {
            findings = processor.process(findings, context);
        }
        metrics.add(metric("finding-processing", startProcessors));

        var startNormalizers = System.nanoTime();
        for (var normalizer : normalizers) {
            findings = normalizer.normalize(findings, context);
        }
        metrics.add(metric("normalization", startNormalizers));

        var startBuild = System.nanoTime();
        var inventory = inventoryBuilder.build(findings, context);
        metrics.add(metric("inventory-building", startBuild));

        var startEnrich = System.nanoTime();
        for (var enricher : enrichers) {
            inventory = enricher.enrich(inventory, context);
        }
        metrics.add(metric("enrichment", startEnrich));

        int itemCount = inventory.items().size();
        int uncertainCount = inventory.uncertain().size();
        LOG.info(() -> "Finished ConfigRadar scan: findings=" + itemCount + ", uncertain=" + uncertainCount);
        return new ScanResult(inventory, diagnostics, metrics);
    }

    private static PerformanceMetric metric(String phase, long startNanos) {
        return new PerformanceMetric(phase, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
