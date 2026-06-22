package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import java.util.List;

/** Ordered component for filtering, deduping, or tagging raw findings. */
public interface FindingProcessor {
    /**
     * Stable processor id used in diagnostics when this stage is made externally pluggable.
     *
     * @return unique processor id
     */
    String id();

    /**
     * Processes raw findings before normalization.
     *
     * @param findings findings from detectors
     * @param context scan context
     * @return processed findings, usually deduped, filtered, or tagged
     */
    List<ScanFinding> process(List<ScanFinding> findings, ScanContext context);
}
