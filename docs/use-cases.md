# Use Cases

## 1. Full Configuration Inventory

Generate `config-inventory.yaml` for one Java/Spring project.

Questions answered:

- what configuration keys exist
- where each key is defined
- where each key is read
- which keys are tied to profiles or environments
- which keys are only read but not defined
- which keys are defined but not read
- which findings are dynamic or uncertain

## 2. Pre-Deployment Configuration Review

Compare two inventories before deployment or code review.

Questions answered:

- which config keys were added
- which config keys were removed
- which values or defaults changed
- which environment/profile-specific values changed
- which code paths started reading new keys
- which changes need manual release confirmation

## 3. Project-Specific Rule Onboarding

Help teams describe project-specific wrappers without Java plugins.

Examples:

```java
AcmeConfig.get("payment.timeout");
ConfigCenter.get("order-service", "payment.timeout");
@AcmeValue("payment.timeout")
```

Questions answered:

- is this an annotation, method call, config file, metadata, or dynamic key pattern
- can it be expressed as YAML custom rules
- does it need a Java detector plugin
- what confidence level should the rule emit

## 4. Downstream Consumption

Let other tools consume the normalized inventory.

First version:

- default YAML consumer only

Future consumers:

- internal deployment platform format
- CI gate format
- owner review CSV
- Markdown release report
- custom YAML schema

## 5. Risk and Quality Checks

Report lightweight warnings on inventory or diff output.

Examples:

- `read-but-not-defined`
- `defined-but-not-read`
- `multiple-env-values`
- `dynamic-key`
- `sensitive-looking-key`
- `missing-default`
- `conditional-only`
- `prod-only-change`
- `cross-env-inconsistent`
