package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.export.AppConfigEntry;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.output.ConsumerContext;
import io.github.hzzzzzx.configradar.core.scan.RedactionPolicy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Shared conversion of config keys/values into the XAC platform's {@link AppConfigEntry} /
 * {@link J2cSecretEntry} shapes. Used by both {@link XacConsumer} (full-inventory export) and
 * {@link XacDiffConsumer} (diff export), so the two never diverge on how a key becomes a secret,
 * how a group is derived, or how a scope is resolved.
 *
 * <p>Scope resolution is delegated to {@link ScopeMapping}: the consumer builds a mapping from the
 * {@code -D scope-mapping} file and {@code -D scope.<profile>} overrides in {@link ConsumerContext},
 * then each entry/secret is built with the scope resolved from its finding's profile.
 */
final class XacEntryBuilder {

    /** Redaction policy used solely to classify sensitive keys for routing. */
    static final RedactionPolicy SENSITIVE = RedactionPolicy.redactSensitive();

    /** Default encrypt type used by the J2C secret template. */
    static final String DEFAULT_ENCRYPT_TYPE = "ADVANCED2.6";

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

    /** The profile of a finding (null when none is set). */
    static String profileOf(ConfigFinding finding) {
        var env = finding.environment();
        return env == null ? null : env.profile();
    }

    /**
     * Builds the scope mapping from context: a {@code -D scope-mapping=<file>} (if present) merged
     * with {@code -D scope.<profile>=<scope>} overrides. Empty mapping (always fallback) when
     * neither is provided.
     */
    static ScopeMapping buildScopeMapping(ConsumerContext context) {
        var fileProp = context.property("scope-mapping");
        if (fileProp != null && !fileProp.isBlank()) {
            try {
                var file = loadMappingFile(fileProp);
                if (file != null) {
                    return ScopeMapping.combined(ScopeMapping.load(file), context.properties());
                }
            } catch (Exception ignored) {
                // best-effort: a bad mapping file falls back to per-profile/placeholder resolution
            }
        }
        return ScopeMapping.fromProperties(context.properties());
    }

    /** Resolves a mapping file path; returns null when it does not exist. */
    private static Path loadMappingFile(String fileProp) {
        var path = Paths.get(fileProp);
        return java.nio.file.Files.exists(path) ? path : null;
    }

    static AppConfigEntry toEntry(String normalizedKey, String value, String scope) {
        return new AppConfigEntry(
            scope,
            groupOf(normalizedKey),
            normalizedKey,
            value,
            SENSITIVE.matchesKey(normalizedKey) ? 1 : null,
            null,
            null,
            null,
            null
        );
    }

    static J2cSecretEntry toSecret(String normalizedKey, String value, ConfigFinding finding, String scope) {
        var underscoreKey = toUnderscore(normalizedKey);
        var type = typeHint(normalizedKey);
        return new J2cSecretEntry(
            underscoreKey,
            null,
            type.isEmpty() ? null : type,
            null,
            "${" + underscoreKey + "}",
            DEFAULT_ENCRYPT_TYPE,
            type.isEmpty() ? null : type,
            scope
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
