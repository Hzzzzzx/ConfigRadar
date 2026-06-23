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
- [Coverage scope](docs/coverage.md)
- [Use cases](docs/use-cases.md)
- [Downstream consumers](docs/downstream-consumers.md)
- [Technical decisions](docs/technical-decisions.md)
- [Implementation flow](docs/implementation-flow.md)
- [Roadmap](docs/roadmap.md)
- [Agent usage skill](skills/config-radar/SKILL.md)

Build the CLI with `./scripts/build.sh`; the agent skill in `skills/config-radar/` documents the supported workflows and exact commands.
