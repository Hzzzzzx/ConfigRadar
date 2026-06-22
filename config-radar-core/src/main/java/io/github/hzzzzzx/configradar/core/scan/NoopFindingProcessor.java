package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import java.util.List;

/** Placeholder processor that preserves findings unchanged. */
public final class NoopFindingProcessor implements FindingProcessor {
    @Override
    public String id() {
        return "noop-finding-processor";
    }

    @Override
    public List<ScanFinding> process(List<ScanFinding> findings, ScanContext context) {
        return List.copyOf(findings);
    }
}
