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
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Detects Kubernetes ConfigMap data and container env definitions. */
public final class KubernetesEnvDetector implements ConfigDetector {
    private static final ObjectReader YAML_READER = YamlSupport.mapper().readerFor(Object.class);

    @Override
    public String id() {
        return "kubernetes-env";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var findings = new ArrayList<ScanFinding>();
        var root = context.input().projectRoot();
        if (root == null) {
            return findings;
        }
        for (var file : context.fileIndex().ofType(FileType.YAML)) {
            var docs = YAML_READER.readValues(new StringReader(java.nio.file.Files.readString(file.path())));
            while (docs.hasNext()) {
                var document = docs.next();
                if (document instanceof Map<?, ?> map && isKubernetes(map)) {
                    readConfigMapData(context, file, map, findings);
                    readContainers(context, file, map, findings);
                }
            }
        }
        return findings;
    }

    private static boolean isKubernetes(Map<?, ?> map) {
        return map.containsKey("kind") && map.containsKey("apiVersion");
    }

    private static void readConfigMapData(
        ScanContext context,
        IndexedFile file,
        Map<?, ?> document,
        List<ScanFinding> findings
    ) {
        if (!"ConfigMap".equals(String.valueOf(document.get("kind"))) || !(document.get("data") instanceof Map<?, ?> data)) {
            return;
        }
        for (var entry : data.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                addFinding(
                    context,
                    file,
                    String.valueOf(entry.getKey()),
                    String.valueOf(entry.getValue()),
                    "config-map-data",
                    Confidence.HIGH,
                    findings
                );
            }
        }
    }

    private static void readContainers(
        ScanContext context,
        IndexedFile file,
        Object node,
        List<ScanFinding> findings
    ) {
        if (node instanceof Map<?, ?> map) {
            readEnv(context, file, map.get("containers"), findings);
            readEnv(context, file, map.get("initContainers"), findings);
            for (var value : map.values()) {
                readContainers(context, file, value, findings);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (var value : list) {
                readContainers(context, file, value, findings);
            }
        }
    }

    private static void readEnv(
        ScanContext context,
        IndexedFile file,
        Object containers,
        List<ScanFinding> findings
    ) {
        if (!(containers instanceof List<?> list)) {
            return;
        }
        for (var container : list) {
            if (!(container instanceof Map<?, ?> map) || !(map.get("env") instanceof List<?> env)) {
                continue;
            }
            for (var item : env) {
                if (item instanceof Map<?, ?> envMap) {
                    readEnvItem(context, file, envMap, findings);
                }
            }
        }
    }

    private static void readEnvItem(
        ScanContext context,
        IndexedFile file,
        Map<?, ?> env,
        List<ScanFinding> findings
    ) {
        var key = string(env.get("name"));
        if (key == null || key.isBlank()) {
            return;
        }
        var value = string(env.get("value"));
        if (value != null) {
            addFinding(context, file, key, value, "env", Confidence.HIGH, findings);
            return;
        }
        var ref = keyRef(env.get("valueFrom"));
        if (ref != null) {
            addFinding(context, file, key, ref.value(), ref.type(), Confidence.MEDIUM, findings);
        }
    }

    private static Ref keyRef(Object valueFrom) {
        if (!(valueFrom instanceof Map<?, ?> map)) {
            return null;
        }
        if (map.get("configMapKeyRef") instanceof Map<?, ?> configMap) {
            return new Ref("config-map-key-ref", refValue(configMap));
        }
        if (map.get("secretKeyRef") instanceof Map<?, ?> secret) {
            return new Ref("secret-key-ref", refValue(secret));
        }
        return null;
    }

    private static String refValue(Map<?, ?> ref) {
        var name = string(ref.get("name"));
        var key = string(ref.get("key"));
        if (name == null || key == null) {
            return String.valueOf(ref);
        }
        return name + ":" + key;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static void addFinding(
        ScanContext context,
        IndexedFile file,
        String key,
        String value,
        String type,
        Confidence confidence,
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
            confidence,
            "kubernetes-env",
            new ExternalDetails("kubernetes", type, null)
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

    private record Ref(String type, String value) {
    }
}
