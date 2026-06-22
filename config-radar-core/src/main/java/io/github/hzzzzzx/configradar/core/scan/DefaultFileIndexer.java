package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Minimal project file indexer used before real build/source-set detection. */
public final class DefaultFileIndexer implements FileIndexer {
    @Override
    public FileIndex index(ScanInput input, ScanOptions options) throws Exception {
        var root = input.projectRoot();
        if (root == null || !Files.isDirectory(root)) {
            throw new IOException("Project root does not exist or is not a directory: " + root);
        }

        try (var paths = Files.walk(root)) {
            var files = paths
                .filter(Files::isRegularFile)
                .filter(path -> !isIgnored(root.relativize(path)))
                .filter(path -> isIncluded(input, root, path))
                .filter(path -> !isExcluded(input, root, path))
                .filter(path -> options.includeTests() || !isTestPath(root.relativize(path)))
                .map(path -> new IndexedFile(path, typeOf(path), scopeOf(root.relativize(path))))
                .toList();
            return new FileIndex(files);
        }
    }

    private static boolean isIncluded(ScanInput input, Path root, Path path) {
        return input.includePaths().isEmpty()
            || input.includePaths().stream().anyMatch(include -> matches(root, path, include));
    }

    private static boolean isExcluded(ScanInput input, Path root, Path path) {
        return input.excludePaths().stream().anyMatch(exclude -> matches(root, path, exclude));
    }

    private static boolean matches(Path root, Path path, Path configured) {
        var absolute = path.toAbsolutePath().normalize();
        var direct = configured.toAbsolutePath().normalize();
        var rooted = root.resolve(configured).toAbsolutePath().normalize();
        return absolute.startsWith(direct) || absolute.startsWith(rooted);
    }

    private static boolean isIgnored(Path relative) {
        var text = relative.toString();
        return text.startsWith(".git/")
            || text.startsWith("target/")
            || text.startsWith("build/")
            || text.contains("/target/")
            || text.contains("/build/");
    }

    private static boolean isTestPath(Path relative) {
        var text = relative.toString();
        return text.contains("src/test/");
    }

    private static Scope scopeOf(Path relative) {
        return isTestPath(relative) ? Scope.TEST : Scope.MAIN;
    }

    private static FileType typeOf(Path path) {
        var name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".java")) {
            return FileType.JAVA;
        }
        if (name.endsWith(".yml") || name.endsWith(".yaml")) {
            return FileType.YAML;
        }
        if (name.endsWith(".properties")) {
            return FileType.PROPERTIES;
        }
        if (name.endsWith(".xml")) {
            return FileType.XML;
        }
        if (name.endsWith(".json")) {
            return FileType.JSON;
        }
        if (name.endsWith(".conf")) {
            return FileType.CONF;
        }
        return FileType.OTHER;
    }
}
