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
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.ValueType;
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
        if (root == null) {
            return findings;
        }
        for (var file : composeFiles(context)) {
            var document = YAML_READER.readValue(file.path().toFile());
            if (!(document instanceof Map<?, ?> rootMap) || !(rootMap.get("services") instanceof Map<?, ?> services)) {
                continue;
            }
            for (var service : services.values()) {
                if (service instanceof Map<?, ?> serviceMap) {
                    addEnvironment(context, file, serviceMap.get("environment"), findings);
                    addArgs(context, file, serviceMap.get("entrypoint"), "entrypoint", findings);
                    addArgs(context, file, serviceMap.get("command"), "command", findings);
                }
            }
        }
        return findings;
    }

    private static List<IndexedFile> composeFiles(ScanContext context) {
        return context.fileIndex().ofType(FileType.YAML).stream()
            .filter(file -> isComposeFile(file.path().getFileName().toString()))
            .toList();
    }

    private static boolean isComposeFile(String name) {
        return name.equals("docker-compose.yml")
            || name.equals("docker-compose.yaml")
            || name.equals("compose.yml")
            || name.equals("compose.yaml");
    }

    private static void addEnvironment(
        ScanContext context,
        IndexedFile file,
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

    private static void addArgs(
        ScanContext context,
        IndexedFile file,
        Object value,
        String type,
        List<ScanFinding> findings
    ) {
        for (var token : argTokens(value)) {
            var pair = argumentPair(token);
            if (pair != null) {
                addFinding(context, file, pair.key(), pair.value(), type, findings);
            }
        }
    }

    private static List<String> argTokens(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (value instanceof String text) {
            return List.of(text.split("\\s+"));
        }
        return List.of();
    }

    private static Pair argumentPair(String token) {
        if (token.startsWith("--")) {
            return pair(token.substring(2));
        }
        if (token.startsWith("-D")) {
            return pair(token.substring(2));
        }
        return null;
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
        IndexedFile file,
        String key,
        String value,
        List<ScanFinding> findings
    ) {
        addFinding(context, file, key, value, "environment", findings);
    }

    private static void addFinding(
        ScanContext context,
        IndexedFile file,
        String key,
        String value,
        String type,
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
            new ExternalDetails("docker-compose", type, null)
        ));
    }

    private static SourceLocation source(Path root, IndexedFile file) {
        return new SourceLocation(
            root.toAbsolutePath().relativize(file.path().toAbsolutePath()).toString(),
            null,
            null,
            SourceKind.YAML,
            file.scope()
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
