package io.github.hzzzzzx.configradar.core.rule;

import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads local config-radar-rules.yaml when present. */
public final class RuleLoader {
    /**
     * Loads declarative scan rules from YAML.
     *
     * <p>A missing or unset rules file is treated as an empty rule set so the default scanner can
     * run without project customization. YAML parsing failures are structural input failures and
     * are thrown to the caller.</p>
     *
     * @param rulesFile optional path to a local rules file
     * @return loaded rules or an empty rule set
     * @throws Exception when the file exists but cannot be read or parsed
     */
    public ConfigRules load(Path rulesFile) throws Exception {
        if (rulesFile == null || !Files.exists(rulesFile)) {
            return ConfigRules.empty();
        }
        return YamlSupport.mapper().readValue(rulesFile.toFile(), ConfigRules.class);
    }
}
