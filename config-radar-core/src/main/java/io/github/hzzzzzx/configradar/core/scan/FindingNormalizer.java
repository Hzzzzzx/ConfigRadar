package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import java.util.List;

/** Ordered component for key/source/environment normalization. */
public interface FindingNormalizer {
    /**
     * Stable normalizer id used for diagnostics and future plugin reporting.
     *
     * @return unique normalizer id
     */
    String id();

    /**
     * Normalizes finding fields such as key, source, role, environment, and output order.
     *
     * @param findings processed findings
     * @param context scan context
     * @return normalized findings
     */
    List<ScanFinding> normalize(List<ScanFinding> findings, ScanContext context);
}
