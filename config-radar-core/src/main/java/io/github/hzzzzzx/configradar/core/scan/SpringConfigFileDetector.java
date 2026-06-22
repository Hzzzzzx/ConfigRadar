package io.github.hzzzzzx.configradar.core.scan;

import com.fasterxml.jackson.databind.ObjectReader;
import io.github.hzzzzzx.configradar.core.io.YamlSupport;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.model.SpringPlaceholderDetails;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import io.github.hzzzzzx.configradar.core.model.ValueType;
import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Detects Spring Boot application/bootstrap files and simple .env definitions. */
public final class SpringConfigFileDetector implements ConfigDetector {
    private static final ObjectReader YAML_READER = YamlSupport.mapper().readerFor(Object.class);

    @Override
    public String id() {
        return "spring-config-file";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        for (var file : context.fileIndex().files()) {
            var type = configuredType(context, file);
            if (type == FileType.UNKNOWN) {
                continue;
            }
            if (type == FileType.YAML) {
                findings.addAll(readYaml(context, file));
            } else if (type == FileType.PROPERTIES || isDotEnv(file.path())) {
                findings.addAll(readProperties(context, file));
            }
        }
        return findings;
    }

    private List<ScanFinding> readYaml(ScanContext context, IndexedFile file) throws Exception {
        var text = Files.readString(file.path());
        var findings = new ArrayList<ScanFinding>();
        var docs = YAML_READER.readValues(text);
        while (docs.hasNextValue()) {
            var document = docs.nextValue();
            flattenYaml(context, file, document, "", profileOf(file.path(), document), findings);
        }
        return findings;
    }

    private void flattenYaml(
        ScanContext context,
        IndexedFile file,
        Object node,
        String prefix,
        String profile,
        List<ScanFinding> findings
    ) {
        if (node instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                var key = String.valueOf(entry.getKey());
                var next = prefix.isBlank() ? key : prefix + "." + key;
                flattenYaml(context, file, entry.getValue(), next, profile, findings);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (var index = 0; index < list.size(); index++) {
                flattenYaml(context, file, list.get(index), prefix + "[" + index + "]", profile, findings);
            }
            return;
        }
        if (!prefix.isBlank() && node != null) {
            var rawValue = String.valueOf(node);
            var line = lineOf(file.path(), prefix);
            findings.add(finding(context, file, prefix, rawValue, profile, line, SourceKind.YAML));
            addPlaceholderReads(context, file, rawValue, profile, line, SourceKind.YAML, findings);
        }
    }

    private List<ScanFinding> readProperties(ScanContext context, IndexedFile file) throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file.path())) {
            properties.load(reader);
        }

        var findings = new ArrayList<ScanFinding>();
        for (var name : properties.stringPropertyNames()) {
            var rawValue = properties.getProperty(name);
            var line = lineOf(file.path(), name);
            findings.add(finding(
                context,
                file,
                name,
                rawValue,
                profileOf(file.path()),
                line,
                SourceKind.PROPERTIES
            ));
            addPlaceholderReads(context, file, rawValue, profileOf(file.path()), line, SourceKind.PROPERTIES, findings);
        }
        return findings;
    }

    private ConfigFinding finding(
        ScanContext context,
        IndexedFile file,
        String key,
        String rawValue,
        String profile,
        Integer line,
        SourceKind sourceKind
    ) {
        return new ConfigFinding(
            key,
            key,
            roleOf(key),
            new ConfigValue(rawValue, rawValue, typeOf(rawValue)),
            null,
            new EnvironmentContext(profile, null, null),
            source(context, file, line, sourceKind),
            Confidence.HIGH,
            id(),
            new UnknownDetails("spring-config-file", key + "=" + rawValue)
        );
    }

    private void addPlaceholderReads(
        ScanContext context,
        IndexedFile file,
        String rawValue,
        String profile,
        Integer line,
        SourceKind sourceKind,
        List<ScanFinding> findings
    ) {
        var start = rawValue.indexOf("${");
        while (start >= 0) {
            var end = rawValue.indexOf('}', start + 2);
            if (end < 0) {
                return;
            }
            addPlaceholderRead(context, file, rawValue.substring(start + 2, end), rawValue, profile, line, sourceKind, findings);
            start = rawValue.indexOf("${", end + 1);
        }
    }

    private void addPlaceholderRead(
        ScanContext context,
        IndexedFile file,
        String body,
        String rawValue,
        String profile,
        Integer line,
        SourceKind sourceKind,
        List<ScanFinding> findings
    ) {
        var colon = body.indexOf(':');
        var key = colon < 0 ? body : body.substring(0, colon);
        var defaultValue = colon < 0 ? null : body.substring(colon + 1);
        if (key.isBlank()) {
            return;
        }
        findings.add(new ConfigFinding(
            key,
            key,
            FindingRole.READ,
            null,
            defaultValue == null ? null : new ConfigValue(defaultValue, defaultValue, typeOf(defaultValue)),
            new EnvironmentContext(profile, null, null),
            source(context, file, line, sourceKind),
            Confidence.HIGH,
            id(),
            new SpringPlaceholderDetails(defaultValue, rawValue)
        ));
    }

    private static FindingRole roleOf(String key) {
        return key.equals("spring.profiles")
            || key.startsWith("spring.profiles.")
            || key.equals("spring.config.import")
            || key.startsWith("spring.config.activate.")
            ? FindingRole.METADATA
            : FindingRole.DEFINE;
    }

    private SourceLocation source(ScanContext context, IndexedFile file, Integer line, SourceKind sourceKind) {
        var root = context.input().projectRoot();
        var path = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
        return new SourceLocation(path.toString(), line, null, sourceKind, file.scope());
    }

    private static boolean isSpringConfig(Path path) {
        var name = path.getFileName().toString();
        return name.equals("application.yml")
            || name.equals("application.yaml")
            || name.equals("application.properties")
            || name.equals("bootstrap.yml")
            || name.equals("bootstrap.yaml")
            || name.equals("bootstrap.properties")
            || name.matches("(application|bootstrap)-[^.]+\\.(yml|yaml|properties)");
    }

    private static FileType configuredType(ScanContext context, IndexedFile file) {
        if (isSpringConfig(file.path())) {
            return file.type();
        }
        if (isDotEnv(file.path())) {
            return FileType.PROPERTIES;
        }
        var root = context.input().projectRoot();
        var relative = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
        for (var rule : context.rules().configFiles()) {
            if (rule.pattern() == null) {
                continue;
            }
            var matcher = FileSystems.getDefault().getPathMatcher("glob:" + rule.pattern());
            if (matcher.matches(relative)) {
                return rule.format() == FileType.UNKNOWN ? file.type() : rule.format();
            }
        }
        return FileType.UNKNOWN;
    }

    private static String profileOf(Path path) {
        var name = path.getFileName().toString();
        if (name.equals(".env")) {
            return null;
        }
        if (name.startsWith(".env.")) {
            return name.substring(".env.".length());
        }
        var prefix = name.startsWith("application-") ? "application-" : name.startsWith("bootstrap-") ? "bootstrap-" : null;
        if (prefix == null) {
            return null;
        }
        var dot = name.indexOf('.');
        return dot < 0 ? null : name.substring(prefix.length(), dot);
    }

    private static boolean isDotEnv(Path path) {
        var name = path.getFileName().toString();
        return name.equals(".env") || name.startsWith(".env.");
    }

    private static String profileOf(Path path, Object document) {
        var activated = valueAt(document, "spring", "config", "activate", "on-profile");
        if (activated != null && !activated.isBlank()) {
            return activated;
        }
        var legacy = valueAt(document, "spring", "profiles");
        return legacy == null || legacy.isBlank() ? profileOf(path) : legacy;
    }

    private static String valueAt(Object node, String... path) {
        Object current = node;
        for (var segment : path) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current == null ? null : String.valueOf(current);
    }

    private static ValueType typeOf(String value) {
        if (value == null) {
            return ValueType.UNKNOWN;
        }
        var text = value.trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
            return ValueType.BOOLEAN;
        }
        if (text.matches("-?\\d+")) {
            return ValueType.INTEGER;
        }
        if (text.startsWith("${") && text.endsWith("}")) {
            return ValueType.PLACEHOLDER;
        }
        return ValueType.STRING;
    }

    private static Integer lineOf(Path path, String key) {
        try (var lines = new BufferedReader(new StringReader(Files.readString(path)))) {
            String line;
            var number = 1;
            var last = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
            while ((line = lines.readLine()) != null) {
                var trimmed = line.trim();
                if (trimmed.startsWith(key + "=")
                    || trimmed.startsWith(key + ":")
                    || trimmed.startsWith(last + ":")) {
                    return number;
                }
                number++;
            }
        } catch (Exception ignored) {
            // ponytail: line number is evidence only; missing it must not fail scanning.
        }
        return null;
    }
}
