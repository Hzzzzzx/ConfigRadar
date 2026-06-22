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
import java.util.LinkedHashMap;
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
        var documents = new ArrayList<Document>();
        for (var file : context.fileIndex().ofType(FileType.YAML)) {
            var docs = YAML_READER.readValues(new StringReader(java.nio.file.Files.readString(file.path())));
            while (docs.hasNext()) {
                var document = docs.next();
                if (document instanceof Map<?, ?> map && isKubernetes(map)) {
                    documents.add(new Document(file, map));
                }
            }
        }
        var configMaps = configMaps(documents);
        for (var document : documents) {
            readConfigMapData(context, document.file(), document.map(), findings);
            readContainers(context, document.file(), document.map(), configMaps, findings);
        }
        return findings;
    }

    private static boolean isKubernetes(Map<?, ?> map) {
        return map.containsKey("kind") && map.containsKey("apiVersion");
    }

    private static Map<String, Map<String, String>> configMaps(List<Document> documents) {
        var result = new LinkedHashMap<String, Map<String, String>>();
        for (var document : documents) {
            var map = document.map();
            if (!"ConfigMap".equals(String.valueOf(map.get("kind"))) || !(map.get("data") instanceof Map<?, ?> data)) {
                continue;
            }
            var name = metadataName(map);
            if (name != null && !name.isBlank()) {
                result.put(name, stringMap(data));
            }
        }
        return result;
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
        Map<String, Map<String, String>> configMaps,
        List<ScanFinding> findings
    ) {
        if (node instanceof Map<?, ?> map) {
            readEnv(context, file, map.get("containers"), configMaps, findings);
            readEnv(context, file, map.get("initContainers"), configMaps, findings);
            for (var value : map.values()) {
                readContainers(context, file, value, configMaps, findings);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (var value : list) {
                readContainers(context, file, value, configMaps, findings);
            }
        }
    }

    private static void readEnv(
        ScanContext context,
        IndexedFile file,
        Object containers,
        Map<String, Map<String, String>> configMaps,
        List<ScanFinding> findings
    ) {
        if (!(containers instanceof List<?> list)) {
            return;
        }
        for (var container : list) {
            if (!(container instanceof Map<?, ?> map)) {
                continue;
            }
            if (map.get("env") instanceof List<?> env) {
                for (var item : env) {
                    if (item instanceof Map<?, ?> envMap) {
                        readEnvItem(context, file, envMap, findings);
                    }
                }
            }
            if (map.get("envFrom") instanceof List<?> envFrom) {
                for (var item : envFrom) {
                    if (item instanceof Map<?, ?> envFromMap) {
                        readEnvFromItem(context, file, envFromMap, configMaps, findings);
                    }
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

    private static void readEnvFromItem(
        ScanContext context,
        IndexedFile file,
        Map<?, ?> envFrom,
        Map<String, Map<String, String>> configMaps,
        List<ScanFinding> findings
    ) {
        if (envFrom.get("configMapRef") instanceof Map<?, ?> configMap) {
            var name = string(configMap.get("name"));
            if (name != null && !name.isBlank()) {
                addMetadata(context, file, "kubernetes.env-from.config-map." + name, name, "env-from-config-map-ref", findings);
                addConfigMapEnvFrom(context, file, name, string(envFrom.get("prefix")), configMaps, findings);
            }
        }
        if (envFrom.get("secretRef") instanceof Map<?, ?> secret) {
            var name = string(secret.get("name"));
            if (name != null && !name.isBlank()) {
                addMetadata(context, file, "kubernetes.env-from.secret." + name, name, "env-from-secret-ref", findings);
            }
        }
    }

    private static void addConfigMapEnvFrom(
        ScanContext context,
        IndexedFile file,
        String name,
        String prefix,
        Map<String, Map<String, String>> configMaps,
        List<ScanFinding> findings
    ) {
        var data = configMaps.get(name);
        if (data == null) {
            return;
        }
        var actualPrefix = prefix == null ? "" : prefix;
        for (var entry : data.entrySet()) {
            addFinding(
                context,
                file,
                actualPrefix + entry.getKey(),
                entry.getValue(),
                "env-from-config-map-data",
                Confidence.MEDIUM,
                findings
            );
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

    private static String metadataName(Map<?, ?> document) {
        if (document.get("metadata") instanceof Map<?, ?> metadata) {
            return string(metadata.get("name"));
        }
        return null;
    }

    private static Map<String, String> stringMap(Map<?, ?> data) {
        var result = new LinkedHashMap<String, String>();
        for (var entry : data.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    private static void addMetadata(
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
            FindingRole.METADATA,
            new ConfigValue(value, value, ValueType.STRING),
            null,
            EnvironmentContext.none(),
            source(context.input().projectRoot(), file),
            Confidence.MEDIUM,
            "kubernetes-env",
            new ExternalDetails("kubernetes", type, null)
        ));
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

    private record Document(IndexedFile file, Map<?, ?> map) {
    }
}
