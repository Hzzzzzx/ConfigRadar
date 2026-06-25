# ConfigRadar

ConfigRadar is an extensible configuration inventory and change-analysis tool for Java/Spring applications. It scans source and resources **without running the app** and produces a stable YAML inventory that can be diffed across releases.

It answers:

- what configuration keys exist
- where each key is defined and where it is read
- which profiles, regions, or namespaces a key belongs to
- what the static value and default value are
- which keys are dynamic or uncertain and need human review
- what changed in config between two versions

It does **not** fetch runtime-resolved values, scan inside packaged JARs, or require the target app to build. Those are later phases.

## Requirements

- JDK 21+
- Maven 3.9+

## Quick start

```bash
git clone <repo> ConfigRadar
cd ConfigRadar
./scripts/build.sh                  # builds dist/config-radar-cli.jar
```

Then scan any Java/Spring project:

```bash
java -jar dist/config-radar-cli.jar inventory /path/to/spring-project -o config-inventory.yaml
```

## Commands

### `inventory` — generate a config inventory of one project

```bash
java -jar dist/config-radar-cli.jar inventory <project-root> -o <output.yaml> [options]
```

| Option | Purpose |
|---|---|
| `<project-root>` | Project to scan (required). |
| `-o, --output <f>` | Inventory YAML output path (required). |
| `--profile <p>` | Default profile hint for findings without one. |
| `--region <r>` / `--namespace <n>` | Default region/namespace hints. |
| `--rules <f>` | Custom `config-radar-rules.yaml`. Omit to auto-load `<project-root>/config-radar-rules.yaml`. |
| `--include <path>` / `--exclude <path>` | Path-prefix filters (repeatable). |
| `--include-tests` | Scan `src/test` too (off by default). |
| `--redact-sensitive` | Mask values of sensitive-looking keys (password/secret/token). |
| `--consumer <id>` | Output consumer: `yaml` (default ConfigRadar inventory), `default` (plain app_configs), `html` (self-contained report), or `xac` (XAC platform artifact). Default: `yaml`. See [downstream consumers](docs/downstream-consumers.md). |
| `-D <key=value>` | Downstream context property (repeatable), passed to the consumer, e.g. `-D scope=prod`. |
| `--metrics <f>` | Write timing/diagnostics sidecar YAML. |
| `--enable-codegraph` | Opt-in semantic detector (needs the `codegraph` tool installed). |

### `diff` — compare two inventories

```bash
java -jar dist/config-radar-cli.jar diff --base <before.yaml> --head <after.yaml> -o <diff.yaml>
```

| Option | Purpose |
|---|---|
| `--base <f>` / `--head <f>` | The two inventories to compare (required). |
| `-o, --output <f>` | Diff YAML output path (required). |
| `--redact-sensitive` | Mask sensitive values in the diff output. |

The diff reports `added` / `removed` / `changed` (value or default changed) plus `uncertainChanged` (new dynamic access) and new `checks`. **Use the same `--profile/--region/--namespace` on both inventories** or matching keys appear as add+remove instead of changed.

The workflow is always: scan two states → diff the two YAML files. ConfigRadar never diffs source directly.

### `config-diff` — config changes between two git commits

```bash
java -jar dist/config-radar-cli.jar config-diff --repo <repo> --base-ref <commit> --head-ref <commit> -o <changes.yaml> [options]
```

| Option | Purpose |
|---|---|
| `--repo <path>` | Git repository path (default: current directory). |
| `--base-ref` / `--head-ref` | Commit-ish to compare (required): tag, branch, or sha. |
| `-o, --output <f>` | Changes YAML output path (required). |
| `--profile` / `--region` / `--namespace` | Default hints (applied to both sides). |
| `--redact-sensitive` | Mask sensitive-looking values. |

One command, end-to-end: materializes both commits into temporary worktrees, scans each into an inventory, diffs them, then **filters the diff to only the changes touching files that git reports as changed** between the two refs. This reduces noise — only config keys whose source file actually changed are reported. Worktrees are cleaned up automatically.

### `export` — convert an inventory to a config-center format

```bash
java -jar dist/config-radar-cli.jar export --inventory <config-inventory.yaml> -o <output.yaml> [--format default|xac] [options]
```

| Option | Purpose |
|---|---|
| `--inventory <f>` | Inventory YAML to convert (required). |
| `-o, --output <f>` | Output YAML path (required). |
| `--format <m>` | Output mode: `default` (plain config inventory) or `xac` (XAC deployment-platform artifact). Default: `default`. |
| `--missing <f>` | Optional output for keys missing a value (read in code but never defined, no default). |
| `--merge <f>` | Optional filled missing-file to merge values back into the export. |

Two output modes:

- **`default`** — plain config inventory. Every key goes into `app_configs`; sensitive keys are kept inline and flagged with `secret: 1`. No J2C section. This is the general-purpose config statistic.
- **`xac`** — artifact for the internal XAC deployment platform. Output follows the platform manifest shape: fixed `apiVersion`/`kind` headers, an application `metadata.name`, and all config under `data`. Sensitive keys (password/secret/token/credential) are routed to `data.J2C.secrets` with placeholder passwords, while `data.app_configs` holds only non-sensitive keys:

```yaml
apiVersion: "com.huawei.his.appconfigcenter.v3"   # fixed
kind: "his.appconfigcenter"                        # fixed
metadata:
  name: "my-app"                                   # application name (-D name=... or project name)
data:
  app_configs:                                     # plain config (non-sensitive)
    - scope: "${app_deploy_unit_name}"
      group_name: server
      config_key: server-port
      config_value: "8080"
      secret: 0
      sub_application_id: ""
      version: "1.0"
      docker_version: "1.0"
      remark: ""
  J2C:                                             # sensitive config
    secrets:
      - key: db_password
        init_source: input
        type: mysql
        account: ""
        password: "${db_password}"
        encrypt_type: ADVANCED2.6
        remark: mysql
      scope: "${app_deploy_unit_name}"
```

**Deduplication:** when a key is defined in multiple sources (e.g. `application.yml` and `application-prod.yml`), the value from the highest Spring-priority source wins (an approximation based on file name and profile).

**Sensitive partitioning:** keys whose names look sensitive are routed to `J2C.secrets` with a placeholder password derived from the key (underscore form), since the real secret is provisioned out-of-band. `secret` in `app_configs` is therefore always `0`.

**Missing-value workflow:** keys read in code but never defined and without a default go to `--missing`. Fill in `config_value` there, then run with `--merge` to produce the final YAML:

```bash
# 1. export, producing the main list + a missing-value list
java -jar dist/config-radar-cli.jar export --inventory inv.yaml -o app-configs.yaml --missing missing.yaml
# 2. (manually or via a skill) fill config_value in missing.yaml
# 3. merge the filled values back and emit the final YAML
java -jar dist/config-radar-cli.jar export --inventory inv.yaml -o final.yaml --merge missing-filled.yaml
```

## Reading the inventory output

```yaml
summary:        # counts: keys, findings, uncertain, checks, diagnostics
items:          # confirmed config facts
  - key / normalizedKey
  - role: DEFINE | READ | CONDITION | METADATA
  - value / defaultValue
  - environment: profile/region/namespace
  - source: path/line/sourceKind(JAVA|YAML|PROPERTIES|XML|JSON)/scope
  - confidence: HIGH | MEDIUM | LOW
  - detectorId
uncertain:       # dynamic/unresolved keys (assembled at runtime, map/args driven)
checks:          # automated warnings
diagnostics:     # any detector failures (empty when healthy)
```

- `items` vs `uncertain`: confirmed keys are in `items`; dynamic keys that cannot be statically resolved go to `uncertain` rather than being guessed.
- `role`: `DEFINE` = declared in a config file; `READ` = consumed in code; `CONDITION` = gates behavior (`@ConditionalOnProperty`); `METADATA` = profile/import wiring.
- The same key may appear multiple times (defined in a file AND read in several places). That is correct.

### Automated checks

The inventory/diff automatically flags:

- `sensitive-looking-key` (WARNING): key name looks like a secret.
- `remote-config-source` (WARNING): reference to a remote config center (Spring Cloud Config/Nacos/Apollo) — review externally.
- `dynamic-config-key` (ERROR): an uncertain/dynamic key that could not be resolved statically.

## What gets scanned

Built-in, no setup needed: Spring `application*`/`bootstrap*` files (yml/yaml/properties), `.env`, `${...}` placeholders with defaults, HOCON, Spring Boot metadata JSON, runtime XML (`web.xml`, Logback/Log4j2 `springProperty`), Dockerfile/docker-compose/Kubernetes deploy files, and Java source reads — `@Value` (field/param/constructor), `@ConfigurationProperties`, `@ConditionalOnProperty`, `@Profile`, `@PropertySource`, `Environment.getProperty`, `System.getProperty/getenv`, `Binder.bind`, `static final` constants and string concatenation, Apollo/Nacos entries, JNDI, and more.

## Project-specific rules

When the project uses a custom config API built-in detectors don't know (e.g. `AcmeConfig.get("x")`, `@AcmeValue("x")`, or a non-standard config file), add a `config-radar-rules.yaml` in the project root. It is auto-loaded when `--rules` is omitted:

```yaml
methodCalls:
  - id: acme-config-get
    owner: AcmeConfig          # owner type or variable name
    method: get
    keyArg: 0                  # zero-based index of the key argument
    defaultArg: 1              # optional: default-value argument index
    confidence: HIGH
    role: READ

annotations:
  - id: acme-value
    type: AcmeValue            # annotation type name
    keyAttribute: value        # attribute holding the key
    defaultAttribute: fallback # optional: attribute holding the default
    confidence: MEDIUM
    role: READ

configFiles:
  - id: deploy-props
    pattern: deploy/*.properties   # glob relative to project root
    format: PROPERTIES             # PROPERTIES | YAML
    scope: RUNTIME                 # RUNTIME | MAIN
```

`keyArg`/`defaultArg`/`valueArg` are zero-based argument indexes; `keyAttribute`/`valueAttribute`/`defaultAttribute` are annotation attribute names. Re-run the inventory; new keys appear with the rule's `id` as `detectorId`.

## Consuming the inventory downstream

The default YAML inventory is the integration point. See [docs/downstream-consumers.md](docs/downstream-consumers.md) for how to read it programmatically, transform it into your own format, or plug in a custom consumer when you embed ConfigRadar as a library.

## Development

Run tests:

```bash
mvn test
```

Modules:

- `config-radar-core` — models, scan pipeline, detectors, rules, diff, YAML output
- `config-radar-cli` — picocli command line (`inventory`, `diff`)

## Documentation

- [Project plan](docs/plan.md)
- [Architecture](docs/architecture.md)
- [Current architecture and data flow](docs/current-architecture-and-flow.md)
- [Coverage scope](docs/coverage.md)
- [Use cases](docs/use-cases.md)
- [Downstream consumers](docs/downstream-consumers.md)
- [Technical decisions](docs/technical-decisions.md)
- [Implementation flow](docs/implementation-flow.md)
- [Roadmap](docs/roadmap.md)
- [Agent usage skill](skills/config-radar/SKILL.md)

Build the CLI with `./scripts/build.sh`; the agent skill in `skills/config-radar/` documents the supported workflows and exact commands.
