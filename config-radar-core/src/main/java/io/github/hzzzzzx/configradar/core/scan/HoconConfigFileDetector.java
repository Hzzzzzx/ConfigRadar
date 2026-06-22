package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Detects simple Typesafe Config HOCON key/value files. */
public final class HoconConfigFileDetector implements ConfigDetector {
    @Override
    public String id() {
        return "hocon-config-file";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        for (var file : context.fileIndex().ofType(FileType.CONF)) {
            if (!isHoconConfig(file)) {
                continue;
            }
            var lines = Files.readAllLines(file.path());
            var blockDepth = 0;
            for (var index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                var stripped = line.strip();
                if (blockDepth > 0) {
                    blockDepth += braces(stripped);
                    continue;
                }
                if (stripped.endsWith("{")) {
                    blockDepth += braces(stripped);
                    continue;
                }
                var pair = pair(line);
                if (pair == null) {
                    continue;
                }
                findings.add(new ConfigFinding(
                    pair.key(),
                    pair.key(),
                    FindingRole.DEFINE,
                    new ConfigValue(pair.value(), pair.value(), typeOf(pair.value())),
                    null,
                    EnvironmentContext.none(),
                    source(context, file, index + 1),
                    Confidence.MEDIUM,
                    id(),
                    new UnknownDetails("hocon-config-file", pair.key())
                ));
            }
        }
        return findings;
    }

    private static int braces(String text) {
        return (int) text.chars().filter(character -> character == '{').count()
            - (int) text.chars().filter(character -> character == '}').count();
    }

    private static boolean isHoconConfig(IndexedFile file) {
        var name = file.path().getFileName().toString();
        return name.equals("application.conf") || name.equals("reference.conf");
    }

    private static Pair pair(String line) {
        var text = line.strip();
        if (text.isBlank() || text.startsWith("#") || text.startsWith("//") || text.endsWith("{")) {
            return null;
        }
        var split = split(text);
        if (split <= 0) {
            return null;
        }
        var key = text.substring(0, split).strip();
        if (key.isBlank() || key.contains(" ")) {
            return null;
        }
        var value = text.substring(split + 1).strip();
        if (value.isBlank()) {
            return null;
        }
        return new Pair(unquote(key), unquote(value));
    }

    private static int split(String text) {
        var equals = text.indexOf('=');
        var colon = text.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }

    private static String unquote(String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1);
        }
        return text;
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

    private static SourceLocation source(ScanContext context, IndexedFile file, int line) {
        var root = context.input().projectRoot();
        var path = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
        return new SourceLocation(path.toString(), line, null, SourceKind.UNKNOWN, file.scope());
    }

    private record Pair(String key, String value) {
    }
}
