package io.github.hzzzzzx.configradar.core.scan;

import com.fasterxml.jackson.databind.ObjectReader;
import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Detects docker-compose service environment definitions. */
public final class DockerComposeEnvDetector implements ConfigDetector {
    private static final ObjectReader YAML_READER = YamlSupport.mapper().readerFor(Object.class);

    @Override
    public String id() {
        return "docker-compose-env";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        var root = context.input().projectRoot();
        if (root == null || !Files.isDirectory(root)) {
            return findings;
        }
        for (var file : composeFiles(root)) {
            var document = YAML_READER.readValue(file.toFile());
            if (!(document instanceof Map<?, ?> rootMap) || !(rootMap.get("services") instanceof Map<?, ?> services)) {
                continue;
            }
            for (var service : services.values()) {
                if (service instanceof Map<?, ?> serviceMap) {
                    addEnvironment(context, file, serviceMap.get("environment"), findings);
                }
            }
        }
        return findings;
    }

    private static List<Path> composeFiles(Path root) throws Exception {
        try (var paths = Files.walk(root, 3)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> isComposeFile(path.getFileName().toString()))
                .toList();
        }
    }

    private static boolean isComposeFile(String name) {
        return name.equals("docker-compose.yml")
            || name.equals("docker-compose.yaml")
            || name.equals("compose.yml")
            || name.equals("compose.yaml");
    }

    private static void addEnvironment(
        ScanContext context,
        Path file,
        Object environment,
        List<ScanFinding> findings
    ) {
        if (environment instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    addFinding(context, file, String.valueOf(entry.getKey()), String.valueOf(entry.getValue()), findings);
                }
            }
            return;
        }
        if (environment instanceof List<?> list) {
            for (var item : list) {
                var pair = pair(String.valueOf(item));
                if (pair != null) {
                    addFinding(context, file, pair.key(), pair.value(), findings);
                }
            }
        }
    }

    private static Pair pair(String text) {
        var split = text.indexOf('=');
        if (split <= 0) {
            return null;
        }
        return new Pair(text.substring(0, split), text.substring(split + 1));
    }

    private static void addFinding(
        ScanContext context,
        Path file,
        String key,
        String value,
        List<ScanFinding> findings
    ) {
        findings.add(new ConfigFinding(
            key,
            key,
            FindingRole.DEFINE,
            new ConfigValue(value, value, typeOf(value)),
            null,
            EnvironmentContext.none(),
            source(context.input().projectRoot(), file),
            Confidence.MEDIUM,
            "docker-compose-env",
            new ExternalDetails("docker-compose", "environment", null)
        ));
    }

    private static SourceLocation source(Path root, Path file) {
        return new SourceLocation(
            root.toAbsolutePath().relativize(file.toAbsolutePath()).toString(),
            null,
            null,
            SourceKind.YAML,
            Scope.MAIN
        );
    }

    private static ValueType typeOf(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return ValueType.BOOLEAN;
        }
        if (value.matches("-?\\d+")) {
            return ValueType.INTEGER;
        }
        return ValueType.STRING;
    }

    private record Pair(String key, String value) {
    }
}
