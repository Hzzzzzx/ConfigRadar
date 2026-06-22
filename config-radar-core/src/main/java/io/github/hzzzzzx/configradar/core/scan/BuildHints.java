package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.BuildTool;
import java.nio.file.Path;
import java.util.List;

/** Build/source-root hints for source-set planning. */
public record BuildHints(
    BuildTool buildTool,
    List<Path> sourceRoots,
    List<Path> resourceRoots
) {
    public BuildHints {
        buildTool = buildTool == null ? BuildTool.UNKNOWN : buildTool;
        sourceRoots = List.copyOf(sourceRoots == null ? List.of() : sourceRoots);
        resourceRoots = List.copyOf(resourceRoots == null ? List.of() : resourceRoots);
    }

    public static BuildHints unknown() {
        return new BuildHints(BuildTool.UNKNOWN, List.of(), List.of());
    }
}
