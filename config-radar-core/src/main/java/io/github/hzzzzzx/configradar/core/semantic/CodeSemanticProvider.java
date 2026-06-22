package io.github.hzzzzzx.configradar.core.semantic;

import io.github.hzzzzzx.configradar.core.scan.ScanContext;
import java.nio.file.Path;
import java.util.List;

/** Optional code semantic backend used by detector adapters. */
public interface CodeSemanticProvider {
    boolean available(Path projectRoot);

    List<CodeConfigUsage> findConfigUsages(ScanContext context) throws Exception;
}
