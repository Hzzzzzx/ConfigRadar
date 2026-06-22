package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ScanMode;

/** Execution switches for one scan. */
public record ScanOptions(
    boolean includeTests,
    boolean includeUncertain,
    int parallelism,
    int javaParallelism,
    ScanMode scanMode
) {
    public ScanOptions {
        int cpu = Runtime.getRuntime().availableProcessors();
        parallelism = parallelism <= 0 ? Math.min(cpu, 8) : parallelism;
        javaParallelism = javaParallelism <= 0 ? Math.min(2, parallelism) : javaParallelism;
        scanMode = scanMode == null ? ScanMode.STATIC_SOURCE : scanMode;
    }

    public static ScanOptions defaults() {
        return new ScanOptions(false, true, 0, 0, ScanMode.STATIC_SOURCE);
    }
}
