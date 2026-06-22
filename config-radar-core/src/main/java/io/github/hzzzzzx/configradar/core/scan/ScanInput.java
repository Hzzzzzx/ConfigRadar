package io.github.hzzzzzx.configradar.core.scan;

import java.nio.file.Path;
import java.util.List;

/** User input for a scan. */
public record ScanInput(
    Path projectRoot,
    List<Path> includePaths,
    List<Path> excludePaths,
    Path rulesFile,
    EnvironmentHints environmentHints,
    BuildHints buildHints
) {
    public ScanInput {
        includePaths = List.copyOf(includePaths == null ? List.of() : includePaths);
        excludePaths = List.copyOf(excludePaths == null ? List.of() : excludePaths);
        environmentHints = environmentHints == null ? EnvironmentHints.none() : environmentHints;
        buildHints = buildHints == null ? BuildHints.unknown() : buildHints;
    }

    public static ScanInput of(Path projectRoot) {
        return new ScanInput(projectRoot, List.of(), List.of(), null, EnvironmentHints.none(), BuildHints.unknown());
    }
}
