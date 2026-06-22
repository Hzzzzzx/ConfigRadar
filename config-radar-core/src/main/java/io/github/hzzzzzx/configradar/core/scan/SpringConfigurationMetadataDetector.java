package io.github.hzzzzzx.configradar.core.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Detects Spring Boot configuration metadata JSON files. */
public final class SpringConfigurationMetadataDetector implements ConfigDetector {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String id() {
        return "spring-configuration-metadata";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        for (var file : context.fileIndex().ofType(FileType.JSON)) {
            if (!isMetadataFile(file)) {
                continue;
            }
            var root = JSON.readTree(Files.readString(file.path()));
            var properties = root.path("properties");
            if (!properties.isArray()) {
                continue;
            }
            for (var property : properties) {
                var name = text(property, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                findings.add(new ConfigFinding(
                    name,
                    name,
                    FindingRole.READ,
                    null,
                    defaultValue(property),
                    EnvironmentContext.none(),
                    source(context, file),
                    Confidence.HIGH,
                    id(),
                    new UnknownDetails("spring-configuration-metadata", property.toString())
                ));
            }
        }
        return findings;
    }

    private static boolean isMetadataFile(IndexedFile file) {
        var name = file.path().getFileName().toString();
        return name.equals("spring-configuration-metadata.json")
            || name.equals("additional-spring-configuration-metadata.json");
    }

    private static ConfigValue defaultValue(JsonNode property) {
        var value = property.get("defaultValue");
        return value == null || value.isNull()
            ? null
            : new ConfigValue(value.asText(), value.asText(), typeOf(value, text(property, "type")));
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static ValueType typeOf(JsonNode value, String declaredType) {
        if (value.isBoolean() || "java.lang.Boolean".equals(declaredType) || "boolean".equals(declaredType)) {
            return ValueType.BOOLEAN;
        }
        if (value.isIntegralNumber() || "java.lang.Integer".equals(declaredType) || "int".equals(declaredType)
            || "java.lang.Long".equals(declaredType) || "long".equals(declaredType)) {
            return ValueType.INTEGER;
        }
        return ValueType.STRING;
    }

    private static SourceLocation source(ScanContext context, IndexedFile file) {
        var root = context.input().projectRoot();
        var path = root == null ? file.path() : root.toAbsolutePath().relativize(file.path().toAbsolutePath());
        return new SourceLocation(path.toString(), null, null, SourceKind.JSON, file.scope());
    }
}
