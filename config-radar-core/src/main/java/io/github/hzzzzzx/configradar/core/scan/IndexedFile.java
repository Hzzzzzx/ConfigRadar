package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.Scope;
import java.nio.file.Path;

/** One file classified by FileIndexer. */
public record IndexedFile(
    Path path,
    FileType type,
    Scope scope
) {
}
