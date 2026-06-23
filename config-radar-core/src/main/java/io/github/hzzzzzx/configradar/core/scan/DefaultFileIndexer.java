package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.Scope;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Minimal project file indexer used before real build/source-set detection. */
public final class DefaultFileIndexer implements FileIndexer {

    /**
     * Directory names that are never source/resource roots and are pruned during the walk.
     * Pruning (skipping the whole subtree) keeps large monorepos or IDE-managed trees
     * (e.g. {@code node_modules}, {@code .idea}) from blowing up indexing time and memory.
     */
    private static final Set<String> PRUNED_DIRECTORIES = Set.of(
        ".git",
        ".idea",
        ".gradle",
        ".svn",
        ".hg",
        ".mvn",
        ".next",
        ".nuxt",
        ".codegraph",
        "node_modules",
        "bower_components",
        "target",
        "build",
        "out",
        "dist",
        "bin",
        "venv",
        ".venv",
        "__pycache__",
        ".cache"
    );

    @Override
    public FileIndex index(ScanInput input, ScanOptions options) throws Exception {
        var root = input.projectRoot();
        if (root == null || !Files.isDirectory(root)) {
            throw new IOException("Project root does not exist or is not a directory: " + root);
        }

        var files = new ArrayList<IndexedFile>();
        java.nio.file.Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                var name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                if (PRUNED_DIRECTORIES.contains(name)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                var relative = root.relativize(path);
                if (!isIncluded(input, root, path) || isExcluded(input, root, path)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!options.includeTests() && isTestPath(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                files.add(new IndexedFile(path, typeOf(path), scopeOf(relative)));
                return FileVisitResult.CONTINUE;
            }
        });
        return new FileIndex(files);
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

    private static boolean isTestPath(Path relative) {
        for (var segment : relative) {
            if (segment.toString().equals("test") || segment.toString().equals("testFixtures")) {
                return true;
            }
        }
        return false;
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
