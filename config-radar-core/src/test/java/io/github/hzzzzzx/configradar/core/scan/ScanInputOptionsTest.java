package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.BuildTool;
import io.github.hzzzzzx.configradar.core.model.ScanMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScanInputOptionsTest {
    @Test
    void scanInputDefaultsHintsAndProtectsPathLists() {
        var includes = new ArrayList<>(List.of(Path.of("src/main/java")));
        var excludes = new ArrayList<>(List.of(Path.of("target")));

        var input = new ScanInput(Path.of("demo"), includes, excludes, null, null, null);
        includes.clear();
        excludes.clear();

        assertEquals(Path.of("demo"), input.projectRoot());
        assertEquals(List.of(Path.of("src/main/java")), input.includePaths());
        assertEquals(List.of(Path.of("target")), input.excludePaths());
        assertEquals(EnvironmentHints.none(), input.environmentHints());
        assertEquals(BuildHints.unknown(), input.buildHints());
        assertThrows(UnsupportedOperationException.class, () -> input.includePaths().add(Path.of("src/test/java")));
    }

    @Test
    void buildHintsDefaultsAndProtectsPathLists() {
        var sourceRoots = new ArrayList<>(List.of(Path.of("src/main/java")));
        var resourceRoots = new ArrayList<>(List.of(Path.of("src/main/resources")));

        var hints = new BuildHints(null, sourceRoots, resourceRoots);
        sourceRoots.clear();
        resourceRoots.clear();

        assertEquals(BuildTool.UNKNOWN, hints.buildTool());
        assertEquals(List.of(Path.of("src/main/java")), hints.sourceRoots());
        assertEquals(List.of(Path.of("src/main/resources")), hints.resourceRoots());
        assertThrows(UnsupportedOperationException.class, () -> hints.sourceRoots().add(Path.of("generated")));
    }

    @Test
    void scanOptionsDefaultsInvalidParallelismAndNullMode() {
        var options = new ScanOptions(false, true, 0, 0, null);

        assertFalse(options.includeTests());
        assertTrue(options.includeUncertain());
        assertTrue(options.parallelism() > 0);
        assertTrue(options.parallelism() <= 8);
        assertTrue(options.javaParallelism() > 0);
        assertTrue(options.javaParallelism() <= options.parallelism());
        assertEquals(ScanMode.STATIC_SOURCE, options.scanMode());
    }
}
