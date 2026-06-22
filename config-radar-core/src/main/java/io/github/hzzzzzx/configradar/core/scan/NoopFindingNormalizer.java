package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import java.util.Comparator;
import java.util.List;

/** First normalizer only makes output ordering stable. */
public final class NoopFindingNormalizer implements FindingNormalizer {
    @Override
    public String id() {
        return "noop-finding-normalizer";
    }

    @Override
    public List<ScanFinding> normalize(List<ScanFinding> findings, ScanContext context) {
        return findings.stream().sorted(stableOrder()).toList();
    }

    private static Comparator<ScanFinding> stableOrder() {
        return Comparator
            .comparing((ScanFinding finding) -> finding.source().path())
            .thenComparing(finding -> finding.source().line(), Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ScanFinding::detectorId)
            .thenComparing(NoopFindingNormalizer::keyOrExpression);
    }

    private static String keyOrExpression(ScanFinding finding) {
        if (finding instanceof ConfigFinding config) {
            return config.key();
        }
        if (finding instanceof UncertainFinding uncertain) {
            return uncertain.expression();
        }
        return "";
    }
}
