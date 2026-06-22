package io.github.hzzzzzx.configradar.core.scan;

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
        if (root == null) {
            return findings;
        }
        for (var file : dockerfiles(context)) {
            var lines = logicalLines(Files.readAllLines(file.path()));
            for (var line : lines) {
                for (var pair : envPairs(line.text())) {
                    addFinding(findings, root, file, line.number(), pair, "env");
                }
                for (var pair : commandPairs(line.text())) {
                    addFinding(findings, root, file, line.number(), pair, "command");
                }
            }
        }
        return findings;
    }

    private void addFinding(
        List<ScanFinding> findings,
        Path root,
        IndexedFile file,
        int line,
        Pair pair,
        String type
    ) {
        findings.add(new ConfigFinding(
            pair.key(),
            pair.key(),
            FindingRole.DEFINE,
            new ConfigValue(pair.value(), pair.value(), typeOf(pair.value())),
            null,
            EnvironmentContext.none(),
            source(root, file, line),
            Confidence.MEDIUM,
            id(),
            new ExternalDetails("docker", type, null)
        ));
    }

    private static List<IndexedFile> dockerfiles(ScanContext context) {
        return context.fileIndex().ofType(FileType.OTHER).stream()
            .filter(file -> file.path().getFileName().toString().startsWith("Dockerfile"))
            .toList();
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

    private static List<Pair> commandPairs(String line) {
        if (!line.startsWith("CMD ") && !line.startsWith("ENTRYPOINT ")) {
            return List.of();
        }
        var split = line.indexOf(' ');
        if (split < 0) {
            return List.of();
        }
        return tokens(line.substring(split + 1).replace('[', ' ').replace(']', ' ').replace(',', ' ')).stream()
            .map(DockerfileEnvDetector::argumentPair)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private static Pair argumentPair(String token) {
        if (token.startsWith("--")) {
            return equalsPair(token.substring(2));
        }
        if (token.startsWith("-D")) {
            return equalsPair(token.substring(2));
        }
        return null;
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

    private static SourceLocation source(Path root, IndexedFile file, int line) {
        return new SourceLocation(
            root.toAbsolutePath().relativize(file.path().toAbsolutePath()).toString(),
            line,
            null,
            SourceKind.UNKNOWN,
            file.scope()
        );
    }

    private record Line(int number, String text) {
    }

    private record Pair(String key, String value) {
    }
}
