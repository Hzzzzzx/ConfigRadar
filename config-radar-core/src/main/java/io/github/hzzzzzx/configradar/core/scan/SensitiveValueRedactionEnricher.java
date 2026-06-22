package io.github.hzzzzzx.configradar.core.scan;

import com.fasterxml.jackson.databind.node.TextNode;
import io.github.hzzzzzx.configradar.core.model.ConfigFinding;
import io.github.hzzzzzx.configradar.core.model.ConfigInventory;
import io.github.hzzzzzx.configradar.core.model.ConfigValue;
import io.github.hzzzzzx.configradar.core.model.ConfigCenterDetails;
import io.github.hzzzzzx.configradar.core.model.ExternalDetails;
import io.github.hzzzzzx.configradar.core.model.FindingDetails;
import io.github.hzzzzzx.configradar.core.model.JavaSystemPropertyDetails;
import io.github.hzzzzzx.configradar.core.model.SpringPlaceholderDetails;
import io.github.hzzzzzx.configradar.core.model.UnknownDetails;
import java.util.ArrayList;

/** Masks values for keys that look sensitive when redaction is enabled. */
public final class SensitiveValueRedactionEnricher implements InventoryEnricher {
    @Override
    public String id() {
        return "sensitive-value-redaction";
    }

    @Override
    public ConfigInventory enrich(ConfigInventory inventory, ScanContext context) {
        var policy = context.options().redactionPolicy();
        return redact(inventory, policy);
    }

    public ConfigInventory redact(ConfigInventory inventory, RedactionPolicy policy) {
        if (!policy.enabled()) {
            return inventory;
        }
        var items = new ArrayList<ConfigFinding>(inventory.items().size());
        for (var item : inventory.items()) {
            items.add(shouldRedact(item, policy) ? redact(item, policy.replacement()) : item);
        }
        return new ConfigInventory(
            inventory.schemaVersion(),
            inventory.project(),
            inventory.summary(),
            items,
            inventory.uncertain(),
            inventory.checks(),
            inventory.diagnostics()
        );
    }

    private static boolean shouldRedact(ConfigFinding finding, RedactionPolicy policy) {
        return policy.matchesKey(finding.normalizedKey() == null ? finding.key() : finding.normalizedKey());
    }

    private static ConfigFinding redact(ConfigFinding finding, String replacement) {
        return new ConfigFinding(
            finding.key(),
            finding.normalizedKey(),
            finding.role(),
            mask(finding.value(), replacement),
            mask(finding.defaultValue(), replacement),
            finding.environment(),
            finding.source(),
            finding.confidence(),
            finding.detectorId(),
            mask(finding.details(), replacement)
        );
    }

    private static ConfigValue mask(ConfigValue value, String replacement) {
        return value == null ? null : new ConfigValue(replacement, replacement, value.type());
    }

    private static FindingDetails mask(FindingDetails details, String replacement) {
        if (details instanceof SpringPlaceholderDetails placeholder) {
            return new SpringPlaceholderDetails(replacement, replacement);
        }
        if (details instanceof JavaSystemPropertyDetails systemProperty) {
            return new JavaSystemPropertyDetails(
                systemProperty.defaultValue() == null ? null : replacement,
                systemProperty.fromConstant()
            );
        }
        if (details instanceof ConfigCenterDetails configCenter) {
            return new ConfigCenterDetails(
                configCenter.namespace(),
                configCenter.group(),
                configCenter.dataId(),
                configCenter.defaultValue() == null ? null : replacement
            );
        }
        if (details instanceof UnknownDetails unknown) {
            return new UnknownDetails(unknown.reason(), replacement);
        }
        if (details instanceof ExternalDetails external) {
            return new ExternalDetails(external.namespace(), external.type(), TextNode.valueOf(replacement));
        }
        return details;
    }
}
