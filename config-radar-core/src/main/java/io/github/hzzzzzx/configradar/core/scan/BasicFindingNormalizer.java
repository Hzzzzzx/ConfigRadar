package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Normalizes simple key variants and keeps output ordering stable. */
public final class BasicFindingNormalizer implements FindingNormalizer {
    @Override
    public String id() {
        return "basic-finding-normalizer";
    }

    @Override
    public List<ScanFinding> normalize(List<ScanFinding> findings, ScanContext context) {
        return findings.stream()
            .map(BasicFindingNormalizer::normalizeFinding)
            .sorted(stableOrder())
            .toList();
    }

    private static ScanFinding normalizeFinding(ScanFinding finding) {
        if (!(finding instanceof ConfigFinding config)) {
            return finding;
        }
        return new ConfigFinding(
            config.key(),
            normalizeKey(config.normalizedKey()),
            config.role(),
            config.value(),
            config.defaultValue(),
            config.environment(),
            config.source(),
            config.confidence(),
            config.detectorId(),
            config.details()
        );
    }

    static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        var normalized = new StringBuilder();
        var previous = '\0';
        for (var index = 0; index < key.length(); index++) {
            var current = key.charAt(index);
            var next = index + 1 < key.length() ? key.charAt(index + 1) : '\0';
            if (current == '_' || current == '-') {
                normalized.append('-');
            } else if (Character.isUpperCase(current)) {
                if (index > 0
                    && previous != '.'
                    && previous != '-'
                    && previous != '_'
                    && (!Character.isUpperCase(previous) || Character.isLowerCase(next))) {
                    normalized.append('-');
                }
                normalized.append(Character.toLowerCase(current));
            } else {
                normalized.append(Character.toLowerCase(current));
            }
            previous = current;
        }
        return normalized.toString().replaceAll("-+", "-").toLowerCase(Locale.ROOT);
    }

    private static Comparator<ScanFinding> stableOrder() {
        return Comparator
            .comparing((ScanFinding finding) -> finding.source().path())
            .thenComparing(finding -> finding.source().line(), Comparator.nullsLast(Integer::compareTo))
            .thenComparing(ScanFinding::detectorId)
            .thenComparing(BasicFindingNormalizer::keyOrExpression);
    }

    private static String keyOrExpression(ScanFinding finding) {
        if (finding instanceof ConfigFinding config) {
            return config.normalizedKey();
        }
        if (finding instanceof UncertainFinding uncertain) {
            return uncertain.expression();
        }
        return "";
    }
}
