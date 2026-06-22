package io.github.hzzzzzx.configradar.core.pack;

import io.github.hzzzzzx.configradar.core.rule.ConfigRule;
import io.github.hzzzzzx.configradar.core.scan.ConfigDetector;
import java.util.List;

/** Future ecosystem pack contribution point. MVP does not load external packs yet. */
public interface DetectorPack {
    /**
     * Pack id used in logs and diagnostics.
     *
     * @return unique pack id
     */
    String id();

    /**
     * Detectors contributed by this pack.
     *
     * @return detectors to append to the detector registry
     */
    List<ConfigDetector> detectors();

    /**
     * Declarative rules contributed by this pack.
     *
     * @return rules to merge with project-local rules
     */
    List<ConfigRule> rules();
}
