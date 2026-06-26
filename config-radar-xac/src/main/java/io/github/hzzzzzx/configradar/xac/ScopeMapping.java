package io.github.hzzzzzx.configradar.xac;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Resolves a deployment {@code scope} for a config key based on its Spring profile.
 *
 * <p>Test environments often have multiple deploy units; the same config key needs a different
 * {@code scope} per environment. Instead of hardcoding {@code ${app_deploy_unit_name}}, the scope
 * can be derived from the finding's profile via a mapping.
 *
 * <p>Resolution priority (highest first):
 * <ol>
 *   <li>CLI override: {@code -D scope.<profile>=xxx} (from {@link #fromProperties(Map)} / combined).</li>
 *   <li>File rule: exact {@code profile} match.</li>
 *   <li>File rule: {@code profilePattern} regex match (first match wins, in file order).</li>
 *   <li>File rule: the entry with {@code default: true}.</li>
 *   <li>Fallback: {@code ${app_deploy_unit_name}} (the legacy placeholder).</li>
 * </ol>
 *
 * <p>Mapping file shape ({@code -D scope-mapping=mapping.yaml}):
 * <pre>{@code
 * rules:
 *   - profile: "prod"
 *     scope: "obp-prod"
 *   - profilePattern: ".*beta.*"
 *     scope: "obp-beta"
 *   - default: true
 *     scope: "obp-test-01"
 * }</pre>
 */
public final class ScopeMapping {

    /** The placeholder scope used when no mapping resolves a profile. */
    public static final String FALLBACK_SCOPE = "${app_deploy_unit_name}";

    private final List<Rule> rules;
    private final Map<String, String> overrides;

    private ScopeMapping(List<Rule> rules, Map<String, String> overrides) {
        this.rules = List.copyOf(rules);
        this.overrides = Map.copyOf(overrides);
    }

    /** An empty mapping that always falls back to {@link #FALLBACK_SCOPE}. */
    public static ScopeMapping empty() {
        return new ScopeMapping(List.of(), Map.of());
    }

    /** Loads rules from a YAML file ({@code rules: [...]}). Overrides are empty. */
    public static ScopeMapping load(Path file) throws java.io.IOException {
        var mapper = YamlSupport.mapper();
        @SuppressWarnings("unchecked")
        var root = (Map<String, Object>) mapper.readValue(file.toFile(), Map.class);
        var rawRules = root instanceof Map ? root.get("rules") : null;
        var rules = new ArrayList<Rule>();
        if (rawRules instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?, ?> m) {
                    rules.add(Rule.from(m));
                }
            }
        }
        return new ScopeMapping(rules, Map.of());
    }

    /** Builds a mapping purely from {@code -D scope.<profile>=xxx} properties (exact-match rules). */
    public static ScopeMapping fromProperties(Map<String, String> properties) {
        var overrides = new LinkedHashMap<String, String>();
        if (properties != null) {
            for (var entry : properties.entrySet()) {
                if (entry.getKey() != null && entry.getKey().startsWith("scope.")) {
                    var profile = entry.getKey().substring("scope.".length());
                    if (!profile.isEmpty() && entry.getValue() != null && !entry.getValue().isBlank()) {
                        overrides.put(profile, entry.getValue());
                    }
                }
            }
        }
        return new ScopeMapping(List.of(), overrides);
    }

    /**
     * Merges file rules with CLI overrides. Overrides take precedence over file rules at
     * resolution time regardless of insertion order.
     */
    public static ScopeMapping combined(ScopeMapping fileMapping, Map<String, String> overrideProperties) {
        var overrides = new LinkedHashMap<String, String>();
        overrides.putAll(fileMapping.overrides);
        overrides.putAll(fromProperties(overrideProperties).overrides);
        return new ScopeMapping(fileMapping.rules, overrides);
    }

    /**
     * Resolves the scope for a profile; null/blank profile skips profile-specific matching.
     * Priority: CLI override > exact {@code profile} match > {@code profilePattern} regex match
     * (first match in file order) > {@code default: true} rule > {@link #FALLBACK_SCOPE}.
     */
    public String resolve(String profile) {
        // 1. CLI override (highest priority).
        if (profile != null && !profile.isBlank()) {
            var override = overrides.get(profile);
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        // 2. exact profile match (any exact rule beats any regex rule, regardless of file order).
        if (profile != null && !profile.isBlank()) {
            for (var rule : rules) {
                if (!rule.isDefault() && rule.isExactMatch(profile)) {
                    return rule.scope();
                }
            }
        }
        // 3. profilePattern regex match (first match in file order).
        if (profile != null && !profile.isBlank()) {
            for (var rule : rules) {
                if (!rule.isDefault() && rule.isRegexMatch(profile)) {
                    return rule.scope();
                }
            }
        }
        // 4. default rule.
        for (var rule : rules) {
            if (rule.isDefault()) {
                return rule.scope();
            }
        }
        // 5. fallback.
        return FALLBACK_SCOPE;
    }

    /** One rule in the mapping file. At most one of profile/profilePattern/default applies. */
    public record Rule(String profile, String profilePattern, boolean isDefault, String scope) {

        /** Parses a rule from a YAML mapping node. */
        @SuppressWarnings("unchecked")
        static Rule from(Map<?, ?> m) {
            var profile = stringOf(m.get("profile"));
            var pattern = stringOf(m.get("profilePattern"));
            var defaultFlag = Boolean.TRUE.equals(m.get("default"));
            var scope = stringOf(m.get("scope"));
            return new Rule(profile, pattern, defaultFlag, scope);
        }

        /** True when this rule has an exact {@code profile} equal to {@code p}. */
        boolean isExactMatch(String p) {
            return profile != null && !profile.isBlank() && profile.equals(p);
        }

        /** True when this rule has a {@code profilePattern} regex that matches {@code p}. */
        boolean isRegexMatch(String p) {
            if (profilePattern == null || profilePattern.isBlank() || p == null || p.isBlank()) {
                return false;
            }
            try {
                return Pattern.compile(profilePattern).matcher(p).matches();
            } catch (java.util.regex.PatternSyntaxException ignored) {
                return false;
            }
        }

        private static String stringOf(Object o) {
            return o == null ? null : String.valueOf(o);
        }
    }
}
