package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.export.AppConfigCenterExporter;
import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import java.util.Locale;

/**
 * Shared conversion of config keys/values into the XAC platform's {@link AppConfigEntry} /
 * {@link J2cSecretEntry} shapes. Used by both {@link XacConsumer} (full-inventory export) and
 * {@link XacDiffConsumer} (diff export), so the two never diverge on how a key becomes a secret,
 * how a group is derived, etc.
 *
 * <p>Constants that are identical for both consumers live here as well.
 */
final class XacEntryBuilder {

    /** Redaction policy used solely to classify sensitive keys for routing. */
    static final RedactionPolicy SENSITIVE = RedactionPolicy.redactSensitive();

    /** Placeholder scope used for every entry; deploy-time metadata ConfigRadar cannot know. */
    static final String DEFAULT_SCOPE = "${app_deploy_unit_name}";

    /** Default encrypt type used by the J2C secret template. */
    static final String DEFAULT_ENCRYPT_TYPE = "ADVANCED2.6";

    /** Default init source for J2C secrets (manual input). */
    static final String DEFAULT_INIT_SOURCE = "input";

    private XacEntryBuilder() {
    }

    /** The raw value to emit for a finding, preferring the resolved value over the default. */
    static String valueOr(ConfigFinding finding) {
        if (finding.value() != null) {
            return finding.value().raw();
        }
        if (finding.defaultValue() != null) {
            return finding.defaultValue().raw();
        }
        return "";
    }

    static AppConfigEntry toEntry(String normalizedKey, String value) {
        return new AppConfigEntry(
            DEFAULT_SCOPE,
            groupOf(normalizedKey),
            normalizedKey,
            value,
            SENSITIVE.matchesKey(normalizedKey) ? 1 : 0,
            "",
            AppConfigCenterExporter.DEFAULT_VERSION,
            AppConfigCenterExporter.DEFAULT_VERSION,
            ""
        );
    }

    static J2cSecretEntry toSecret(String normalizedKey, String value, ConfigFinding finding) {
        var underscoreKey = toUnderscore(normalizedKey);
        var type = typeHint(normalizedKey);
        return new J2cSecretEntry(
            underscoreKey,
            DEFAULT_INIT_SOURCE,
            type,
            "",
            "${" + underscoreKey + "}",
            DEFAULT_ENCRYPT_TYPE,
            type.isEmpty() ? "" : type,
            DEFAULT_SCOPE
        );
    }

    static String toUnderscore(String key) {
        if (key == null || key.isBlank()) {
            return "config";
        }
        return key.replace('-', '_').replace('.', '_').toLowerCase(Locale.ROOT);
    }

    static String typeHint(String key) {
        var lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("redis")) {
            return "redis";
        }
        if (lower.startsWith("db.") || lower.contains("datasource") || lower.contains("jdbc")) {
            return "mysql";
        }
        return "";
    }

    static String groupOf(String key) {
        if (key == null || key.isBlank()) {
            return "default";
        }
        var dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : "default";
    }
}
