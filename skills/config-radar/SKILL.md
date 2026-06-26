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
./scripts/build.sh
# produces: dist/config-radar-cli.jar (self-contained executable)
# use --skip-tests for a faster build
```

If the jar already exists, skip this. The two commands below assume the jar path; substitute as needed. Typical path: `dist/config-radar-cli.jar`.

## Four commands

```bash
# Generate an inventory of one project
java -jar <jar> inventory <project-root> -o <inventory.yaml> [options]

# Compare two inventories (e.g. before/after a release)
java -jar <jar> diff --base <before.yaml> --head <after.yaml> -o <diff.yaml> [options]

# Compare config between two git commits directly (checks out each, scans, diffs, filters to changed files)
java -jar <jar> config-diff --repo <repo> --base-ref <commit> --head-ref <commit> -o <out> [options]

# Convert an inventory to the app-config-center format
java -jar <jar> export --inventory <inventory.yaml> -o <app-configs.yaml> [options]
```

There are two diff paths. `diff` compares two already-scanned inventory YAMLs. `config-diff` does the whole thing in one shot against git: it materializes each commit into a temporary worktree, scans both, diffs, and keeps only changes touching files git reports as changed. Use `diff` when you already have inventories (or the two states are not in one git repo); use `config-diff` for a one-command "what config changed between these two commits" answer.

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
| `--consumer <id>` | Render the diff via a consumer in addition to the YAML. Default `yaml` (just the file). `xac` emits the diff as XAC artifacts — see "config-diff options" for the `-o` directory rule and the `-D`/profile hints. |
| `-D key=value` | Downstream context property (repeatable), passed to the consumer. |
| `--profile <p>` / `--region <r>` / `--namespace <n>` | Hints passed to the consumer. |

### config-diff options

| Option | Purpose |
|---|---|
| `--repo <d>` | Git repository path (default: current directory). |
| `--base-ref <c>` / `--head-ref <c>` | Commit-ish to compare (required): tag, branch, or sha. |
| `-o, --output <d>` | Output path (required). **A directory when `--consumer` is set; a file otherwise.** |
| `--profile <p>` / `--region <r>` / `--namespace <n>` | Scan hints applied to both commits. Use the SAME value on both sides or matching keys appear as add+remove instead of changed. |
| `--redact-sensitive` | Mask sensitive-looking values. |
| `--consumer <id>` | Render artifacts beyond the diff YAML. Default `yaml`: `-o` is the exact output file. Any other consumer (e.g. `xac`): `-o` is the output directory; the diff YAML (`config-diff.yaml`) and the consumer's files are written into it. |
| `-D key=value` | Downstream context property (repeatable). For the `xac` consumer: `-D scope-mapping=<file>` and `-D scope.<profile>=<scope>` resolve deploy scopes by profile. |

> If `--consumer` is set, `-o` MUST be a directory (not a file). Giving an existing file path errors out. This differs from the default `yaml` mode where `-o` is the file itself.

### export options

| Option | Purpose |
|---|---|
| `--inventory <f>` | Inventory YAML to convert (required). |
| `-o, --output <f>` | Output YAML (required). |
| `--format <m>` | Output mode: `default` (plain config inventory, sensitive keys kept inline with `secret: 1`) or `xac` (XAC deployment-platform artifact: sensitive keys routed to `J2C.secrets` with placeholder passwords). Default: `default`. |
| `--missing <f>` | Optional output for keys read in code but never defined (no value/default). Fill `config_value` there and feed back via `--merge`. |
| `--merge <f>` | Optional filled missing-file; its values override the inventory for matching keys. |

`--format default` produces a plain `app_configs` list (the general config statistic). `--format xac` partitions the output: non-sensitive keys go to `app_configs`, sensitive keys (password/secret/token/credential) go to `J2C.secrets` with a placeholder password (underscore form of the key, e.g. `db.password` -> `${db_password}`), since the real secret is provisioned out-of-band. In both modes, duplicate keys are deduplicated keeping the highest Spring-priority source, and deploy-time fields (`scope`, `version`, `docker_version`, `sub_application_id`, `remark`, `account`) are left empty.

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

### 2b. Config changes between two git commits, exported to XAC (one command)

Goal: in a single command, find what config changed between two commits/branches and emit XAC deployment artifacts — no manual scan-then-diff, no checkout by hand.

```bash
java -jar <jar> config-diff \
  --repo <repo> --base-ref <old-commit> --head-ref <new-commit> \
  -o scan-output/config-diff/ --consumer xac \
  --profile prod -D scope.prod=obp-prod
```

`-o` is the **output directory** (not a file) under `--consumer`. It receives:

- `config-diff.yaml` — the raw machine-readable diff.
- `app-configs-changed.yaml` — keys to **add or update that have a value**, strict XAC shape (`app_configs` + `J2C.secrets`). A changed key uses its **new** value; a sensitive key (password/secret/token) is routed to `J2C.secrets`. Only written if at least one value-bearing change exists.
- `app-configs-missing.yaml` — added/changed keys that have **no value** (e.g. read in code but never defined). Each entry carries the `source` where it is referenced and a `reason`, for human review. Only written if any exist.
- `removed.yaml` — keys to **delete**, plain list with `config_key`/`group_name`. Only written if any exist.

When you run this, report to the user: how many keys to upsert (and which are sensitive/need out-of-band provisioning), which are valueless and need a human to fill a value (point at each `source`), and which to delete. State that deploy-time fields (`scope`, `version`, etc.) are placeholders unless resolved via `-D scope-mapping`/`-D scope.<profile>`.

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

### 6. Export to the XAC deployment platform (config-center artifact)

Goal: produce an XAC platform artifact (app_configs + J2C.secrets) from the inventory, deduplicating keys by Spring priority.

```bash
# XAC format: app_configs + J2C.secrets, plus a missing-value list
java -jar <jar> export --inventory config-inventory.yaml -o xac.yaml --format xac --missing missing.yaml
```

The output is partitioned: non-sensitive keys go to `app_configs`, sensitive keys (password/secret/token/credential) go to `J2C.secrets` with a placeholder password (underscore form of the key, e.g. `db.password` -> `${db_password}`), since the real secret is provisioned out-of-band. For a plain config inventory without the J2C split, use `--format default` (sensitive keys stay inline flagged `secret: 1`).

Keys read in code but never defined (and without a default) land in `missing.yaml` with an empty `config_value`. Fill those values (manually or via this skill), then merge them back to emit the final YAML:

```bash
java -jar <jar> export --inventory config-inventory.yaml -o final.yaml --merge missing-filled.yaml
```

When helping a user fill the missing list, look up each `config_key` in the inventory: the source evidence (which `@Value`, which file) often reveals the intended value or a sensible default. Always state that deploy-time fields (`scope`, `version`, etc.) are left empty and must be set by the deployment pipeline.

## Rules of thumb for running scans

- **Write outputs into `scan-output/`** (gitignored), organized by purpose: `scan-output/inventory/`, `scan-output/export/{default,xac}/`, `scan-output/diff/`, `scan-output/config-diff/`, `scan-output/html/`. Each consumer writes a fixed filename (`app-configs.yaml` / `config-inventory.yaml` / `config-report.html`), so place one consumer's output per subdirectory to avoid overwrites.
- Run from a clean checkout of the exact version you want to inventory.
- Keep `--profile/--region/--namespace` identical when producing inventories you intend to diff.
- `node_modules`, `target`, `.idea`, `.git` and similar are pruned automatically; no need to exclude them.
- On large projects the first scan may take a few seconds; it is parsing Java source.
- If `diagnostics` is non-empty, a detector failed but the scan continued — review the message; it usually points at an unparseable file.

## When the skill is NOT the right tool

- You need the **runtime-resolved value** of a config key (ConfigRadar only captures static literals/defaults).
- The target is not a Java/Spring project (it scans Java/Spring config sources specifically).
- You need to scan inside a built JAR (Phase 1 scans source/resources, not packaged artifacts).
