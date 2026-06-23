---
name: config-radar
description: Inventory and diff Java/Spring configuration using the ConfigRadar CLI. Use when a user wants to discover what configuration keys exist in a Java/Spring project, where they are defined or read, compare configuration between two versions/releases, review config changes before deployment, audit sensitive or dynamic config, or understand the config footprint of a Spring Boot application. Triggers on phrases like "scan configuration", "config inventory", "what config does this project use", "config diff", "what changed in config", "pre-deploy config review", "配置清单", "配置扫描", "配置差异".
---

# ConfigRadar

ConfigRadar is a static configuration inventory and change-analysis tool for Java/Spring applications. It scans source and resources **without running the app** and produces a stable YAML inventory that can be diffed across releases.

## Prerequisites

- JDK 21+ and Maven 3.9+ to build the CLI, OR a prebuilt `config-radar-cli.jar`.
- The target project is scanned in place; no build or run is required.

## Build the CLI (once)

```bash
mvn -q -pl config-radar-cli -am package -DskipTests
# produces: config-radar-cli/target/config-radar-cli.jar
```

If the jar already exists, skip this. The two commands below assume the jar path; substitute as needed. Typical path: `config-radar-cli/target/config-radar-cli.jar`.

## Two commands

```bash
# Generate an inventory of one project
java -jar <jar> inventory <project-root> -o <inventory.yaml> [options]

# Compare two inventories (e.g. before/after a release)
java -jar <jar> diff --base <before.yaml> --head <after.yaml> -o <diff.yaml> [options]
```

The diff workflow is always: scan two states → diff the two YAML files. ConfigRadar never diffs source directly.

### inventory options

| Option | Purpose |
|---|---|
| `<project-root>` | Project to scan (required). |
| `-o, --output <f>` | Inventory YAML output (required). |
| `--rules <f>` | Custom `config-radar-rules.yaml`. Omit to auto-load `<project-root>/config-radar-rules.yaml`. |
| `--profile <p>` | Default profile hint for findings without one. |
| `--region <r>` / `--namespace <n>` | Default region/namespace hints. |
| `--include <path>` / `--exclude <path>` | Path-prefix filters (repeatable). |
| `--include-tests` | Scan `src/test` too (off by default). |
| `--redact-sensitive` | Mask values of sensitive-looking keys (password/secret/token). |
| `--metrics <f>` | Write timing/diagnostics sidecar YAML. |
| `--enable-codegraph` | Opt-in semantic detector (needs the `codegraph` tool installed). |

### diff options

| Option | Purpose |
|---|---|
| `--base <f>` / `--head <f>` | The two inventories to compare (required). |
| `-o, --output <f>` | Diff YAML output (required). |
| `--redact-sensitive` | Mask sensitive values in the diff. |

## How to read the inventory YAML

```yaml
summary:        # counts: keys, findings, uncertain, checks
items:          # confirmed config facts, each with key + role + source
  - key / normalizedKey
  - role: DEFINE | READ | CONDITION | METADATA
  - value / defaultValue        # raw value + fallback default
  - environment: profile/region/namespace
  - source: path/line/sourceKind(JAVA|YAML|PROPERTIES|XML|JSON)/scope
  - confidence: HIGH | MEDIUM | LOW
  - detectorId                  # which detector found it
uncertain:       # dynamic/unresolved keys that cannot be statically resolved
checks:          # automated warnings (see below)
diagnostics:     # any detector failures (empty when healthy)
```

**Key concepts for interpretation:**
- `items` vs `uncertain`: confirmed keys are in `items`; keys that are dynamic (e.g. assembled at runtime, read from a map/args) go to `uncertain` rather than being guessed.
- `role`: `DEFINE` = declared in a config file; `READ` = consumed in code; `CONDITION` = gates behavior (`@ConditionalOnProperty`); `METADATA` = profile/import source wiring.
- The same key can appear multiple times (defined in a file AND read in several places). That is correct, not duplication.

## What ConfigRadar covers (Phase 1)

Detectors are built in; no setup needed: Spring `application*`/`bootstrap*` files (yml/yaml/properties), `.env`, `${...}` placeholders with defaults, HOCON, Spring Boot metadata JSON, runtime XML (web.xml, Logback/Log4j2 `springProperty`), Dockerfile/docker-compose/Kubernetes deploy files, and Java source reads (`@Value` on fields/params/constructors, `@ConfigurationProperties`, `@ConditionalOnProperty`, `@Profile`, `@PropertySource`, `Environment.getProperty`, `System.getProperty/getenv`, `Binder.bind`, `static final` constants and string concatenation, Apollo/Nacos entries, JNDI, and more).

**Limitations to set expectations honestly:** values are the static literal/default, not the runtime-resolved value; packed JARs are not scanned; full Spring relaxed-binding normalization is partial; third-party wrappers need custom rules (see below).

## Automated checks (already produced)

The inventory/diff automatically flags:
- `sensitive-looking-key` (WARNING): key name looks like a secret (password/secret/token).
- `remote-config-source` (WARNING): reference to a remote config center (Spring Cloud Config/Nacos/Apollo) — value must be reviewed externally.
- `dynamic-config-key` (ERROR): an uncertain/dynamic key that could not be resolved statically.

## Use cases → exact commands

### 1. Full config inventory of a project

Goal: see every config key, where it is defined and read, defaults, profiles, and which are dynamic.

```bash
java -jar <jar> inventory . -o config-inventory.yaml --profile prod --region cn-east-1
```

Then summarize for the user: total keys, how many DEFINE vs READ, any keys read-but-with-no-obvious-definition, the `uncertain` list (these need human review), and the `checks`.

### 2. Pre-deployment / release config review

Goal: find what config changed between two versions or branches before shipping.

```bash
# scan the old state
java -jar <jar> inventory <old-project-root> -o before.yaml --profile prod
# scan the new state (or check out the new branch and scan)
java -jar <jar> inventory <new-project-root> -o after.yaml --profile prod
# compare
java -jar <jar> diff --base before.yaml --head after.yaml -o config-diff.yaml
```

The diff reports `added` / `removed` / `changed` (value or default changed) plus `uncertainChanged` (new dynamic access) and new `checks`. **Use the SAME `--profile/--region/--namespace` on both inventories** or matching keys will appear as add+remove instead of changed.

### 3. Sensitive-config audit

Goal: find secrets and config-center dependencies before release.

```bash
java -jar <jar> inventory . -o config-inventory.yaml --redact-sensitive
```

Read the `checks` section; group results by the `sensitive-looking-key` and `remote-config-source` types. `--redact-sensitive` keeps the report safe to share. Always state that redaction is name-based heuristic, not a guarantee.

### 4. Understand the config footprint of an unfamiliar project

Goal: quickly answer "what config does this Spring app actually use?"

```bash
java -jar <jar> inventory . -o config-inventory.yaml
```

Then give the user: top config prefixes (e.g. `server.*`, `spring.datasource.*`), which files define them, which `@ConfigurationProperties` classes bind them, and any keys only read in code with no file definition (possible missing config).

### 5. Extend coverage for project-specific config wrappers

Goal: the project has a custom config API that built-in detectors don't know (e.g. `AcmeConfig.get("x")`, `@AcmeValue("x")`, or a non-standard config file). ConfigRadar will miss these until told.

Create `<project-root>/config-radar-rules.yaml` (auto-loaded when `--rules` is omitted):

```yaml
methodCalls:
  - id: acme-config-get
    owner: AcmeConfig            # owner type or variable name
    method: get
    keyArg: 0                    # zero-based index of the key argument
    defaultArg: 1                # optional: index of the default-value argument
    confidence: HIGH
    role: READ

annotations:
  - id: acme-value
    type: AcmeValue              # annotation type name
    keyAttribute: value          # attribute holding the key
    defaultAttribute: fallback   # optional: attribute holding the default
    confidence: MEDIUM
    role: READ

configFiles:
  - id: deploy-props
    pattern: deploy/*.properties # glob relative to project root
    format: PROPERTIES           # PROPERTIES | YAML
    scope: RUNTIME               # RUNTIME | MAIN
```

Re-run the inventory; the new keys appear with the rule's `id` as `detectorId`. `keyArg`/`defaultArg`/`valueArg` are zero-based argument indexes; `keyAttribute`/`valueAttribute`/`defaultAttribute` are annotation attribute names.

## Rules of thumb for running scans

- Run from a clean checkout of the exact version you want to inventory.
- Keep `--profile/--region/--namespace` identical when producing inventories you intend to diff.
- `node_modules`, `target`, `.idea`, `.git` and similar are pruned automatically; no need to exclude them.
- On large projects the first scan may take a few seconds; it is parsing Java source.
- If `diagnostics` is non-empty, a detector failed but the scan continued — review the message; it usually points at an unparseable file.

## When the skill is NOT the right tool

- You need the **runtime-resolved value** of a config key (ConfigRadar only captures static literals/defaults).
- The target is not a Java/Spring project (it scans Java/Spring config sources specifically).
- You need to scan inside a built JAR (Phase 1 scans source/resources, not packaged artifacts).
