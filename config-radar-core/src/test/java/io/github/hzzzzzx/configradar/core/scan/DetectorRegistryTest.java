package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DetectorRegistryTest {
    @Test
    void acceptsNullAsEmptyDetectorList() {
        assertTrue(new DetectorRegistry(null).detectors().isEmpty());
        assertTrue(DetectorRegistry.empty().detectors().isEmpty());
    }

    @Test
    void preservesDetectorOrderAndProtectsAgainstExternalMutation() {
        var first = detector("first");
        var second = detector("second");
        var source = new ArrayList<>(List.of(first, second));

        var registry = new DetectorRegistry(source);
        source.clear();

        assertEquals(List.of(first, second), registry.detectors());
        assertThrows(UnsupportedOperationException.class, () -> registry.detectors().add(detector("third")));
    }

    private static ConfigDetector detector(String id) {
        return new ConfigDetector() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public List<ScanFinding> detect(ScanContext context) {
                return List.of();
            }
        };
    }
}
