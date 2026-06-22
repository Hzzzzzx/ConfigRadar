package io.github.hzzzzzx.configradar.core.scan;

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

/** Detects Dockerfile ENV definitions that become runtime environment config. */
public final class DockerfileEnvDetector implements ConfigDetector {
    @Override
    public String id() {
        return "dockerfile-env";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        var root = context.input().projectRoot();
        if (root == null || !Files.isDirectory(root)) {
            return findings;
        }
        for (var file : dockerfiles(root)) {
            var lines = logicalLines(Files.readAllLines(file));
            for (var line : lines) {
                for (var pair : envPairs(line.text())) {
                    findings.add(new ConfigFinding(
                        pair.key(),
                        pair.key(),
                        FindingRole.DEFINE,
                        new ConfigValue(pair.value(), pair.value(), typeOf(pair.value())),
                        null,
                        EnvironmentContext.none(),
                        source(root, file, line.number()),
                        Confidence.MEDIUM,
                        id(),
                        new ExternalDetails("docker", "env", null)
                    ));
                }
            }
        }
        return findings;
    }

    private static List<Path> dockerfiles(Path root) throws Exception {
        try (var paths = Files.walk(root, 3)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().startsWith("Dockerfile"))
                .toList();
        }
    }

    private static List<Line> logicalLines(List<String> lines) {
        var result = new ArrayList<Line>();
        var text = "";
        var start = 1;
        for (var index = 0; index < lines.size(); index++) {
            var line = lines.get(index).strip();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (text.isBlank()) {
                start = index + 1;
            }
            var continued = line.endsWith("\\");
            text += continued ? line.substring(0, line.length() - 1).stripTrailing() + " " : line;
            if (!continued) {
                result.add(new Line(start, text.strip()));
                text = "";
            }
        }
        if (!text.isBlank()) {
            result.add(new Line(start, text.strip()));
        }
        return result;
    }

    private static List<Pair> envPairs(String line) {
        if (!line.startsWith("ENV ")) {
            return List.of();
        }
        var tokens = tokens(line.substring(4).strip());
        if (tokens.isEmpty()) {
            return List.of();
        }
        if (tokens.getFirst().contains("=")) {
            return tokens.stream()
                .map(DockerfileEnvDetector::equalsPair)
                .filter(java.util.Objects::nonNull)
                .toList();
        }
        if (tokens.size() < 2) {
            return List.of();
        }
        return List.of(new Pair(tokens.get(0), tokens.get(1)));
    }

    private static Pair equalsPair(String token) {
        var split = token.indexOf('=');
        if (split <= 0) {
            return null;
        }
        return new Pair(token.substring(0, split), token.substring(split + 1));
    }

    private static List<String> tokens(String text) {
        var result = new ArrayList<String>();
        var current = new StringBuilder();
        var quote = '\0';
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if ((character == '"' || character == '\'') && quote == '\0') {
                quote = character;
                continue;
            }
            if (character == quote) {
                quote = '\0';
                continue;
            }
            if (Character.isWhitespace(character) && quote == '\0') {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(character);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
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

    private static SourceLocation source(Path root, Path file, int line) {
        return new SourceLocation(
            root.toAbsolutePath().relativize(file.toAbsolutePath()).toString(),
            line,
            null,
            SourceKind.UNKNOWN,
            Scope.MAIN
        );
    }

    private record Line(int number, String text) {
    }

    private record Pair(String key, String value) {
    }
}
