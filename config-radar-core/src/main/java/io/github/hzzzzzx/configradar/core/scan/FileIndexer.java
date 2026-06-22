package io.github.hzzzzzx.configradar.core.scan;

/** Component that classifies project files once before detectors run. */
public interface FileIndexer {
    /**
     * Builds a classified file index for the project.
     *
     * @param input scan input containing project root and path filters
     * @param options scan options such as test-source inclusion
     * @return classified files that detectors can consume
     * @throws Exception when the project root cannot be walked
     */
    FileIndex index(ScanInput input, ScanOptions options) throws Exception;
}
