package io.github.hzzzzzx.configradar.xac;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ScopeMappingTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyMappingFallsBackToPlaceholder() {
        assertEquals(ScopeMapping.FALLBACK_SCOPE, ScopeMapping.empty().resolve("prod"));
        // also for null/blank profile
        assertEquals(ScopeMapping.FALLBACK_SCOPE, ScopeMapping.empty().resolve(null));
        assertEquals(ScopeMapping.FALLBACK_SCOPE, ScopeMapping.empty().resolve(""));
    }

    @Test
    void exactProfileMatchWins() throws Exception {
        var mapping = loadMapping("""
            rules:
              - profile: "prod"
                scope: "obp-prod"
            """);
        assertEquals("obp-prod", mapping.resolve("prod"));
        // non-matching profile falls back
        assertEquals(ScopeMapping.FALLBACK_SCOPE, mapping.resolve("dev"));
    }

    @Test
    void regexPatternMatches() throws Exception {
        var mapping = loadMapping("""
            rules:
              - profilePattern: ".*beta.*"
                scope: "obp-beta"
            """);
        assertEquals("obp-beta", mapping.resolve("eu-beta-2"));
        assertEquals("obp-beta", mapping.resolve("beta"));
        assertEquals(ScopeMapping.FALLBACK_SCOPE, mapping.resolve("prod"));
    }

    @Test
    void exactMatchBeatsRegex() throws Exception {
        // a profile that matches both the exact rule and the regex rule -> exact wins (file order
        // has regex first, but exact must still take precedence by category)
        var mapping = loadMapping("""
            rules:
              - profilePattern: ".*"
                scope: "regex-scope"
              - profile: "prod"
                scope: "exact-scope"
            """);
        assertEquals("exact-scope", mapping.resolve("prod"), "exact profile match must beat regex");
        assertEquals("regex-scope", mapping.resolve("dev"));
    }

    @Test
    void defaultRuleIsFallbackAbovePlaceholder() throws Exception {
        var mapping = loadMapping("""
            rules:
              - default: true
                scope: "obp-test-01"
            """);
        assertEquals("obp-test-01", mapping.resolve("anything"));
        assertEquals("obp-test-01", mapping.resolve(null));
    }

    @Test
    void cliOverrideBeatsFileRules() throws Exception {
        var file = loadMapping("""
            rules:
              - profile: "prod"
                scope: "obp-prod"
              - default: true
                scope: "obp-test-01"
            """);
        var combined = ScopeMapping.combined(file, Map.of("scope.prod", "override-prod"));
        assertEquals("override-prod", combined.resolve("prod"), "-D scope.prod overrides file rule");
        // non-overridden profile still uses file default
        assertEquals("obp-test-01", combined.resolve("dev"));
    }

    @Test
    void propertiesOnlyMappingResolvesByProfile() {
        var mapping = ScopeMapping.fromProperties(Map.of(
            "scope.prod", "obp-prod",
            "scope.dev", "obp-dev",
            "unrelated", "ignored"
        ));
        assertEquals("obp-prod", mapping.resolve("prod"));
        assertEquals("obp-dev", mapping.resolve("dev"));
        assertEquals(ScopeMapping.FALLBACK_SCOPE, mapping.resolve("staging"));
    }

    @Test
    void blankScopePropertyIsIgnored() {
        var mapping = ScopeMapping.fromProperties(Map.of(
            "scope.prod", "  ",
            "scope.", "no-profile"
        ));
        assertEquals(ScopeMapping.FALLBACK_SCOPE, mapping.resolve("prod"));
    }

    @Test
    void combinedUsesBothFileRulesAndOverrides() throws Exception {
        var file = loadMapping("""
            rules:
              - profile: "prod"
                scope: "file-prod"
              - profilePattern: ".*beta.*"
                scope: "file-beta"
              - default: true
                scope: "file-default"
            """);
        var combined = ScopeMapping.combined(file, Map.of("scope.prod", "override-prod"));
        assertEquals("override-prod", combined.resolve("prod"));     // override beats file exact
        assertEquals("file-beta", combined.resolve("eu-beta"));      // file regex still works
        assertEquals("file-default", combined.resolve("staging"));   // file default still works
    }

    // --- helper ---

    private ScopeMapping loadMapping(String yaml) throws Exception {
        var file = tempDir.resolve("mapping.yaml");
        Files.writeString(file, yaml);
        return ScopeMapping.load(file);
    }
}
