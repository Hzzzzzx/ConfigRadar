package io.github.hzzzzzx.configradar.core.scan;

import java.util.List;

/** Immutable detector list used by ScanPipeline. */
public record DetectorRegistry(
    List<ConfigDetector> detectors
) {
    public DetectorRegistry {
        detectors = List.copyOf(detectors == null ? List.of() : detectors);
    }

    public static DetectorRegistry empty() {
        return new DetectorRegistry(List.of());
    }
}
