package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
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
            .map(finding -> normalizeFinding(finding, context.input().environmentHints()))
            .sorted(stableOrder())
            .toList();
    }

    private static ScanFinding normalizeFinding(ScanFinding finding, EnvironmentHints hints) {
        if (finding instanceof ConfigFinding config) {
            return new ConfigFinding(
                config.key(),
                normalizeKey(config.normalizedKey()),
                config.role(),
                config.value(),
                config.defaultValue(),
                environment(config.environment(), hints),
                config.source(),
                config.confidence(),
                config.detectorId(),
                config.details()
            );
        }
        if (finding instanceof UncertainFinding uncertain) {
            return new UncertainFinding(
                uncertain.expression(),
                uncertain.reason(),
                uncertain.rootSink(),
                environment(uncertain.environment(), hints),
                uncertain.source(),
                uncertain.confidence(),
                uncertain.detectorId(),
                uncertain.details()
            );
        }
        return finding;
    }

    private static EnvironmentContext environment(EnvironmentContext current, EnvironmentHints hints) {
        return new EnvironmentContext(
            current.profile() == null ? hints.activeProfile() : current.profile(),
            current.region() == null ? hints.region() : current.region(),
            current.namespace() == null ? hints.namespace() : current.namespace()
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
            .thenComparing(BasicFindingNormalizer::profileOrEmpty, Comparator.nullsLast(String::compareTo))
            .thenComparing(ScanFinding::detectorId)
            .thenComparing(BasicFindingNormalizer::keyOrExpression);
    }

    private static String profileOrEmpty(ScanFinding finding) {
        if (finding instanceof ConfigFinding config) {
            return environmentProfile(config.environment());
        }
        if (finding instanceof UncertainFinding uncertain) {
            return environmentProfile(uncertain.environment());
        }
        return null;
    }

    private static String environmentProfile(EnvironmentContext environment) {
        return environment == null ? null : environment.profile();
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
