# Rules and Profiling

## Rule Evolution Loop

Deep tracing is a profiling tool, not the default scan path.

```text
profile project
  -> generate config-radar-rules.yaml candidates
  -> user/agent review
  -> daily inventory/diff uses built-in rules + checked-in rules
  -> uncertain/diff findings suggest rule updates
```

Rules should be reviewable project assets.

Example:

```yaml
methodCalls:
  - id: acme-config-get
    owner: com.acme.Configs
    method: get
    keyArg: 0
    confidence: HIGH
    role: READ
```

Agent assistance should suggest rules, not silently mutate inventory.

## Rule Template

Default template:

```text
config-radar-rules.yaml
```

Example:

```yaml
schemaVersion: config-radar-rules/v1

trace:
  enabled: false
  depth: 1

methodCalls:
  - id: acme-config-get
    owner: com.acme.Configs
    method: get
    keyArg: 0
    confidence: HIGH
    role: READ

annotations:
  - id: acme-value
    type: com.acme.config.AcmeValue
    keyAttribute: value
    confidence: high

configFiles:
  - id: deploy-properties
    pattern: deploy/*.properties
    format: PROPERTIES
    scope: RUNTIME
```

## Dynamic Config Policy

Do not guess dynamic keys.

Instead:

- expose dynamic reads as `uncertain`
- classify the reason
- group by sink, wrapper, package, and module
- track growth in diffs
- mark new dynamic reads as high risk by default

Future runtime snapshot can use uncertain findings as targets to observe resolved keys and masked effective values.

## Rule Enablement

Default early behavior:

- core Java rules enabled
- core Spring rules enabled
- generic placeholder scanning enabled
- default YAML consumer enabled

Later tuning:

- suggest disabling unused packs
- suggest enabling relevant packs
- suggest adding rules for repeated uncertain patterns
- suggest deleting stale rules

Skill integration should explain these suggestions and help users edit `config-radar-rules.yaml`.

## CEL-Guided Tracing

CEL can later guide profiling and filtering without replacing the normalized rule template.

Example:

```yaml
trace:
  enabled: true
  depth: 1
  include:
    - "callee.name in ['get', 'getString', 'getBoolean', 'getProperty']"
    - "callee.owner.endsWith('Config') || callee.owner.endsWith('ConfigCenter')"
  stopWhen:
    - "callee.owner.startsWith('java.')"
    - "depth > 1"
```
