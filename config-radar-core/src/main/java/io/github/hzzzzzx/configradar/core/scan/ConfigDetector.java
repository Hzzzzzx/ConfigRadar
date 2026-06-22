package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import java.util.List;

/** Component that discovers one family of configuration facts. */
public interface ConfigDetector {
    /**
     * Stable detector id written into findings and diagnostics.
     *
     * @return unique id such as {@code spring-value} or {@code system-property}
     */
    String id();

    /**
     * Discovers configuration findings from the scan context.
     *
     * <p>Implementations should not write output files, mutate global state, or compute diffs.
     * Throwing an exception is allowed; {@link ScanPipeline} records it as a diagnostic and keeps
     * the rest of the scan running.</p>
     *
     * @param context immutable scan input, options, rules, and file index
     * @return confirmed and uncertain findings discovered by this detector
     * @throws Exception when the detector cannot finish; the pipeline converts it to diagnostics
     */
    List<ScanFinding> detect(ScanContext context) throws Exception;
}
