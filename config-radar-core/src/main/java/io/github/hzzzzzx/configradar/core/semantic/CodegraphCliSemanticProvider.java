package io.github.hzzzzzx.configradar.core.semantic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.hzzzzzx.configradar.core.model.Confidence;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.Scope;
import io.github.hzzzzzx.configradar.core.model.SourceKind;
import io.github.hzzzzzx.configradar.core.model.SourceLocation;
import io.github.hzzzzzx.configradar.core.scan.ScanContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Uses codegraph's local SQLite index as an optional Java semantic source. */
public final class CodegraphCliSemanticProvider implements CodeSemanticProvider {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern ANNOTATION_DECLARATION = Pattern.compile(
        "((?:\\s*@[^\\n]+\\n)+)\\s*(?:public\\s+)?@interface\\s+(\\w+)"
    );
    private static final Pattern ANNOTATION_USE = Pattern.compile("@([A-Za-z_$][\\w$]*)\\b");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public boolean available(Path projectRoot) {
        return projectRoot != null && commandWorks("codegraph", "--version") && commandWorks("sqlite3", "--version");
    }

    @Override
    public List<CodeConfigUsage> findConfigUsages(ScanContext context) throws Exception {
        var root = context.input().projectRoot();
        if (!available(root)) {
            throw new IllegalStateException("codegraph and sqlite3 are required for --enable-codegraph");
        }

        var absoluteRoot = root.toAbsolutePath().normalize();
        var db = absoluteRoot.resolve(".codegraph/codegraph.db");
        if (Files.exists(db)) {
            run(absoluteRoot, "codegraph", "sync", absoluteRoot.toString());
        } else {
            run(absoluteRoot, "codegraph", "init", "-i", absoluteRoot.toString());
        }

        var files = indexedJavaFiles(db);
        var metaAnnotations = new HashMap<String, String>();
        for (var relative : files) {
            collectMetaAnnotations(absoluteRoot.resolve(relative), metaAnnotations);
        }

        var usages = new ArrayList<CodeConfigUsage>();
        for (var relative : files) {
            usages.addAll(readCustomAnnotationUsages(absoluteRoot, relative, metaAnnotations));
        }
        return usages;
    }

    private static List<Path> indexedJavaFiles(Path db) throws Exception {
        var sql = "select path from files where language = 'java' order by path";
        var json = run(db.getParent(), "sqlite3", "-json", db.toString(), sql);
        var rows = MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        var out = new ArrayList<Path>();
        for (var row : rows) {
            var path = row.get("path");
            if (path != null) {
                out.add(Path.of(path.toString()));
            }
        }
        return out;
    }

    private static void collectMetaAnnotations(Path file, Map<String, String> metaAnnotations) throws Exception {
        var text = Files.readString(file);
        var matcher = ANNOTATION_DECLARATION.matcher(text);
        while (matcher.find()) {
            var annotationBlock = matcher.group(1);
            var annotationName = matcher.group(2);
            var key = firstPlaceholder(annotationBlock);
            if (key != null && annotationName != null) {
                metaAnnotations.put(annotationName, key);
            }
        }
    }

    private static List<CodeConfigUsage> readCustomAnnotationUsages(
        Path root,
        Path relative,
        Map<String, String> metaAnnotations
    ) throws Exception {
        var text = Files.readString(root.resolve(relative));
        var lines = text.split("\\R", -1);
        var usages = new ArrayList<CodeConfigUsage>();
        for (var index = 0; index < lines.length; index++) {
            var line = lines[index];
            if (line.contains("@interface")) {
                continue;
            }
            var matcher = ANNOTATION_USE.matcher(line);
            while (matcher.find()) {
                var annotation = matcher.group(1);
                var key = metaAnnotations.get(annotation);
                if (key == null) {
                    continue;
                }
                usages.add(new CodeConfigUsage(
                    key,
                    UsageKind.CUSTOM_ANNOTATION,
                    new SourceLocation(relative.toString(), index + 1, null, SourceKind.JAVA, scopeOf(relative)),
                    Confidence.MEDIUM,
                    new ExternalDetails("codegraph", "custom-meta-annotation:" + annotation, null)
                ));
            }
        }
        return usages;
    }

    private static String firstPlaceholder(String text) {
        var matcher = PLACEHOLDER.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Scope scopeOf(Path relative) {
        // Match the "test" path segment so it works on '/' (Unix) and '\' (Windows) separators.
        for (var segment : relative) {
            if (segment.toString().equals("test")) {
                return Scope.TEST;
            }
        }
        return Scope.MAIN;
    }

    private static boolean commandWorks(String... command) {
        try {
            var process = new ProcessBuilder(command).redirectErrorStream(true).start();
            return process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String run(Path cwd, String... command) throws Exception {
        var process = new ProcessBuilder(command)
            .directory(cwd.toFile())
            .redirectErrorStream(true)
            .start();
        var finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        var output = new String(process.getInputStream().readAllBytes());
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(command[0] + " timed out");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(command[0] + " failed: " + output.strip());
        }
        return output;
    }
}
