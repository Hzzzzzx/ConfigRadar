package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.EnvironmentContext;
import io.github.hzzzzzx.configradar.core.model.FindingRole;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.semantic.CodeSemanticProvider;
import io.github.hzzzzzx.configradar.core.semantic.CodegraphCliSemanticProvider;
import java.util.List;

/** Optional detector backed by codegraph's local code semantic index. */
public final class CodegraphConfigUsageDetector implements ConfigDetector {
    private final CodeSemanticProvider provider;

    public CodegraphConfigUsageDetector() {
        this(new CodegraphCliSemanticProvider());
    }

    public CodegraphConfigUsageDetector(CodeSemanticProvider provider) {
        this.provider = provider;
    }

    @Override
    public String id() {
        return "codegraph-config-usage";
    }

    @Override
    public List<ScanFinding> detect(ScanContext context) throws Exception {
        var root = context.input().projectRoot();
        if (!provider.available(root)) {
            throw new IllegalStateException("codegraph semantic provider is not available");
        }
        return provider.findConfigUsages(context).stream()
            .<ScanFinding>map(usage -> new ConfigFinding(
                usage.key(),
                usage.key(),
                FindingRole.READ,
                null,
                null,
                EnvironmentContext.none(),
                usage.source(),
                usage.confidence(),
                id(),
                usage.details()
            ))
            .toList();
    }
}
