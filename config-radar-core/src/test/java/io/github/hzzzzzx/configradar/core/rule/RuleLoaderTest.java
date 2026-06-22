package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.scan.FileType;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuleLoaderTest {
    @TempDir
    private Path tempDir;

    @Test
    void missingRulesFileLoadsEmptyRules() throws Exception {
        var rules = new RuleLoader().load(tempDir.resolve("missing.yaml"));

        assertTrue(rules.methodCalls().isEmpty());
        assertTrue(rules.annotations().isEmpty());
        assertTrue(rules.configFiles().isEmpty());
    }

    @Test
    void loadsMethodCallRules() throws Exception {
        var file = tempDir.resolve("config-radar-rules.yaml");
        Files.writeString(file, """
            methodCalls:
              - id: custom-config
                owner: com.acme.Config
                method: get
                keyArg: 0
                valueArg: 1
                confidence: HIGH
                role: CONDITION
            annotations:
              - id: custom-annotation
                type: CustomConfigValue
                keyAttribute: key
                valueAttribute: configuredValue
                role: METADATA
            configFiles:
              - id: custom-file
                pattern: src/main/resources/custom-config.properties
                format: PROPERTIES
                scope: MAIN
            """);

        var rules = new RuleLoader().load(file);

        assertEquals(1, rules.methodCalls().size());
        assertEquals("custom-config", rules.methodCalls().getFirst().id());
        assertEquals(1, rules.methodCalls().getFirst().valueArg());
        assertEquals(Confidence.HIGH, rules.methodCalls().getFirst().confidence());
        assertEquals(FindingRole.CONDITION, rules.methodCalls().getFirst().role());
        assertEquals(1, rules.annotations().size());
        assertEquals(FindingRole.METADATA, rules.annotations().getFirst().role());
        assertEquals("configuredValue", rules.annotations().getFirst().valueAttribute());
        assertEquals(1, rules.configFiles().size());
        assertEquals(FileType.PROPERTIES, rules.configFiles().getFirst().format());
        assertEquals(Scope.MAIN, rules.configFiles().getFirst().scope());
    }
}
