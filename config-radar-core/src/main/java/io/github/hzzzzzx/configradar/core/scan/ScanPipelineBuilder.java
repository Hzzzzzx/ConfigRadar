package io.github.hzzzzzx.configradar.core.scan;

import java.util.ArrayList;
import java.util.List;

/** Small composition helper for built-in and user-contributed scan stages. */
public final class ScanPipelineBuilder {
    private FileIndexer fileIndexer = new DefaultFileIndexer();
    private InventoryBuilder inventoryBuilder = new DefaultInventoryBuilder();
    private final List<ConfigDetector> detectors = new ArrayList<>();
    private final List<FindingProcessor> processors = new ArrayList<>();
    private final List<FindingNormalizer> normalizers = new ArrayList<>();
    private final List<InventoryEnricher> enrichers = new ArrayList<>();

    public ScanPipelineBuilder fileIndexer(FileIndexer newFileIndexer) {
        fileIndexer = newFileIndexer;
        return this;
    }

    public ScanPipelineBuilder detector(ConfigDetector detector) {
        detectors.add(detector);
        return this;
    }

    public ScanPipelineBuilder processor(FindingProcessor processor) {
        processors.add(processor);
        return this;
    }

    public ScanPipelineBuilder normalizer(FindingNormalizer normalizer) {
        normalizers.add(normalizer);
        return this;
    }

    public ScanPipelineBuilder inventoryBuilder(InventoryBuilder newInventoryBuilder) {
        inventoryBuilder = newInventoryBuilder;
        return this;
    }

    public ScanPipelineBuilder enricher(InventoryEnricher enricher) {
        enrichers.add(enricher);
        return this;
    }

    public ScanPipeline build() {
        return new ScanPipeline(
            fileIndexer,
            new DetectorRegistry(detectors),
            processors,
            normalizers,
            inventoryBuilder,
            enrichers
        );
    }
}
