package io.github.hzzzzzx.configradar.core.scan;

import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.InventorySummary;
import io.github.hzzzzzx.configradar.core.model.ProjectInfo;
import io.github.hzzzzzx.configradar.core.model.ScanFinding;
import io.github.hzzzzzx.configradar.core.model.UncertainFinding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Splits internal ScanFinding into public items and uncertain sections. */
public final class DefaultInventoryBuilder implements InventoryBuilder {
    @Override
    public ConfigInventory build(List<ScanFinding> findings, ScanContext context) {
        var items = new ArrayList<ConfigFinding>();
        var uncertain = new ArrayList<UncertainFinding>();
        for (var finding : findings) {
            if (finding instanceof ConfigFinding config) {
                items.add(config);
            } else if (finding instanceof UncertainFinding dynamic) {
                uncertain.add(dynamic);
            }
        }

        var keys = new HashSet<String>();
        items.forEach(item -> keys.add(item.normalizedKey()));
        var summary = new InventorySummary(keys.size(), items.size(), uncertain.size(), 0, 0);
        return new ConfigInventory(
            ConfigInventory.SCHEMA_VERSION,
            new ProjectInfo(projectName(context.input()), "unknown"),
            summary,
            items,
            uncertain,
            List.of(),
            List.of()
        );
    }

    private static String projectName(ScanInput input) {
        var root = input.projectRoot();
        return root == null || root.getFileName() == null ? "unknown" : root.getFileName().toString();
    }
}
