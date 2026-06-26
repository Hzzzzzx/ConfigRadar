package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import java.util.Locale;

/**
 * Approximates Spring Boot externalized-configuration priority from source evidence.
 * Higher number = higher priority (wins). This is a heuristic, not the full Spring order.
 *
 * <p>Lives in the {@code scan} package so that multiple concerns — inventory export
 * ({@code AppConfigCenterExporter}), HTML reporting, XAC consumption, and config-diff winner
 * selection — can reuse the same ranking without cross-package coupling back to {@code export}.
 */
public final class SpringPriority {

    private SpringPriority() {
    }

    /**
     * Approximates Spring Boot externalized-configuration priority from source evidence.
     * Higher number = higher priority (wins). This is a heuristic, not the full Spring order.
     */
    public static int of(ConfigFinding finding) {
        var source = finding.source();
        var path = source.path() == null ? "" : source.path().toLowerCase(Locale.ROOT);
        var profile = finding.environment() == null ? null : finding.environment().profile();
        var fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

        // .env / SPRING_APPLICATION_JSON-style environment overrides (highest).
        if (fileName.startsWith(".env")) {
            return 90;
        }
        // profile-specific application-{profile}.yml/properties.
        if (profile != null && !profile.isBlank()
            && (fileName.startsWith("application-") || fileName.startsWith("bootstrap-"))) {
            return 80;
        }
        // default application.yml / application.properties (profile-less base).
        if (fileName.startsWith("application.yml")
            || fileName.startsWith("application.yaml")
            || fileName.startsWith("application.properties")) {
            return 70;
        }
        // bootstrap files (lower priority than application).
        if (fileName.startsWith("bootstrap.")) {
            return 50;
        }
        // programmatic setProperty in Java source.
        if (source.sourceKind() == SourceKind.JAVA) {
            return 30;
        }
        return 10;
    }
}
