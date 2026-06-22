package io.github.hzzzzzx.configradar.core.model;

/** Internal pipeline item. Output still separates confirmed and uncertain findings. */
public sealed interface ScanFinding permits ConfigFinding, UncertainFinding {
    SourceLocation source();

    Confidence confidence();

    String detectorId();
}
