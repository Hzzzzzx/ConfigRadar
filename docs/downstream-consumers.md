# Downstream Consumers

ConfigRadar produces a stable YAML inventory (`config-inventory/v1`) and a diff (`config-diff/v1`). This document explains how downstream tools consume that output and how to add a custom output format.

There are three integration levels, from simplest to most embedded. **Start with Level 1** — it covers most needs.

## The contract: `config-inventory/v1`

Everything downstream consumes this shape. It is stable across releases; additive optional fields are allowed, renames require a schema bump.

```yaml
schemaVersion: "config-inventory/v1"
project:
  name: "my-app"
  ref: "unknown"            # version/commit when known
summary:
  keys: 221                 # distinct normalizedKey count
  findings: 251             # total items
  uncertain: 13
  checks: 42
  diagnostics: 0
items:                       # confirmed config facts
  - key: "server.port"
    normalizedKey: "server-port"
    role: "DEFINE"           # DEFINE | READ | CONDITION | METADATA
    value:
      raw: "8080"
      normalized: "8080"
      type: "STRING"         # STRING | INTEGER | BOOLEAN | DURATION | PLACEHOLDER | UNKNOWN
    defaultValue:            # optional, present when a fallback default exists
      raw: "8080"
      type: "STRING"
    environment:
      profile: "prod"        # optional
      region: "cn-east-1"    # optional
      namespace: ""          # optional
    source:
      path: "src/main/resources/application.yml"
      line: 12
      symbol: null           # class/method when the finding is in Java source
      sourceKind: "YAML"     # JAVA | YAML | PROPERTIES | XML | JSON | CONF | UNKNOWN
      scope: "MAIN"          # MAIN | TEST | RUNTIME
    confidence: "HIGH"       # HIGH | MEDIUM | LOW
    detectorId: "spring-config-file"
    details:                 # polymorphic, keyed by `type`
      type: "spring-placeholder"
      defaultValue: "8080"
      rawExpression: "${server.port:8080}"
uncertain:                    # dynamic/unresolved keys
  - expression: "prefix + \".url\""
    reason: "STRING_CONCAT"  # STRING_CONCAT | MAP_DRIVEN_KEY | COMMAND_LINE_ARGS | ...
    rootSink: "Environment.getProperty"
    environment: { ... }
    source: { ... }
    confidence: "LOW"
    detectorId: "java-source-config"
    details: { ... }
checks:                       # automated warnings
  - type: "sensitive-looking-key"   # sensitive-looking-key | remote-config-source | dynamic-config-key
    severity: "WARNING"             # WARNING | ERROR
    message: "Configuration key name looks sensitive: REDIS_PASSWORD"
    key: "REDIS_PASSWORD"
    source: { path: ".env", line: 4, ... }
diagnostics:                  # detector failures; empty when healthy
  - severity: "WARNING"
    phase: "detector"
    message: "Skipped unparseable Java source ..."
    componentId: "java-source-config"
```

The diff output (`config-diff/v1`) has `added` / `removed` / `changed` / `uncertainChanged` / `checks`, each entry shaped like an inventory item; `changed` entries additionally carry the changed `field` and `from`/`to` values.

## Level 1 — read the YAML (recommended for most downstream tools)

The default CLI output is YAML you parse in any language. No ConfigRadar dependency is needed downstream.

### Python example

The `details` field uses polymorphic YAML tags (e.g. `!<spring-placeholder>`). Register a catch-all constructor so any tag parses as its underlying mapping:

```python
import yaml

class AnyTagLoader(yaml.FullLoader):
    pass

def construct_unknown(loader, tag_suffix, node):
    if isinstance(node, yaml.MappingNode):
        return loader.construct_mapping(node, deep=True)
    if isinstance(node, yaml.SequenceNode):
        return loader.construct_sequence(node, deep=True)
    return loader.construct_scalar(node)

AnyTagLoader.add_multi_constructor('', construct_unknown)

with open("config-inventory.yaml") as f:
    inv = yaml.load(f, Loader=AnyTagLoader)

keys = {item["key"] for item in inv["items"]}
defined = {item["key"] for item in inv["items"] if item["role"] == "DEFINE"}
read_keys = {item["key"] for item in inv["items"] if item["role"] == "READ"}

# read-but-not-defined: likely missing config
missing = read_keys - defined
# defined-but-not-read: possibly stale
unused = defined - read_keys

# surface sensitive + remote + dynamic checks for review
for check in inv["checks"]:
    print(check["severity"], check["type"], check.get("key", ""))
```

### CI gate example (bash + yq)

Fail a pipeline when new dynamic keys or sensitive keys appear:

```bash
java -jar dist/config-radar-cli.jar inventory . -o config-inventory.yaml
# error if any ERROR-severity check exists
if yq '.checks[] | select(.severity == "ERROR")' config-inventory.yaml | grep -q .; then
  echo "ConfigRadar: unresolved dynamic config keys found" >&2
  exit 1
fi
```

### Diff-driven release review

```bash
java -jar dist/config-radar-cli.jar diff --base before.yaml --head after.yaml -o diff.yaml
# any added keys need sign-off
yq '.added[].key' diff.yaml
```

### Stability guarantees for consumers

- Field names and the `config-inventory/v1` version string are stable.
- `items` are sorted by `(source.path, source.line, environment.profile, detectorId, key)` so diffs are deterministic.
- Additive optional fields may appear in future versions; consumers should ignore unknown fields (most YAML parsers do by default).
- The same key can appear multiple times across files/profiles/read-sites — that is intentional, not duplication. Key on `normalizedKey + role + environment.profile` to dedupe if needed.

## Level 1.5 — the built-in `export` command

ConfigRadar ships a built-in transformer with two output modes, selectable via `--format`:

```bash
java -jar dist/config-radar-cli.jar export --inventory config-inventory.yaml -o out.yaml --format default|xac [--missing missing.yaml] [--merge filled.yaml]
```

**`--format default`** — plain config inventory. Every key goes into `app_configs`; sensitive keys are kept inline and flagged with `secret: 1`. This is the general-purpose config statistic.

**`--format xac`** — artifact for the internal XAC deployment platform. The output is partitioned:

```yaml
app_configs:                  # plain (non-sensitive) config keys
  - scope: "${app_deploy_unit_name}"   # placeholder; deploy-time metadata ConfigRadar cannot know
    group_name: server                 # first key segment
    config_key: server-port            # normalized key
    config_value: "8080"               # value from the highest-priority source
    secret: 0                          # always 0 here; sensitive keys go to J2C
    sub_application_id:                # empty; fill downstream
    version:
    docker_version:
    remark:
J2C:                          # sensitive keys (password/secret/token/credential)
  secrets:
    - key: db_password                 # underscore form of the config key
      init_source: input               # manual input by default
      type: mysql                      # best-effort hint from the key (null when unknown)
      account:                         # empty; fill downstream
      password: "${db_password}"       # placeholder; real secret provisioned out-of-band
      encrypt_type: ADVANCED2.6
      remark: mysql
      scope: "${app_deploy_unit_name}"
```

What the built-in export does for you:

- **Deduplicates** keys defined in multiple sources, keeping the highest Spring-priority value (an approximation from file name/profile).
- **Routes sensitive keys** to `J2C.secrets` with a placeholder password (underscore form), since the real secret is provisioned via an encryption interface.
- **Separates missing values**: keys read in code but never defined and without a default go to `--missing`, using the same schema. Fill `config_value` there and feed back via `--merge`:

```bash
java -jar dist/config-radar-cli.jar export --inventory inv.yaml -o app-configs.yaml --missing missing.yaml
# (manually or via a skill) fill config_value in missing.yaml
java -jar dist/config-radar-cli.jar export --inventory inv.yaml -o final.yaml --merge missing-filled.yaml
```

When the built-in `app_configs` / `J2C` shape is **not** what your platform needs — different fields, different grouping, a CSV, a Markdown report — proceed to Level 2 and write a small transformer. The built-in export is one opinionated shape, not the only one.

## Level 2 — transform the YAML into your own format

When your platform needs a different shape (deployment-platform format, owner-review CSV, Markdown report), transform the YAML rather than reimplementing scanning. Keep ConfigRadar as the scanner of record; your transformer only re-shapes.

Recommended structure for a transformer:

```text
config-inventory.yaml (ConfigRadar output)
  -> your transformer (script or small program)
  -> your-platform-format.yaml / .csv / .md
```

Example: a CSV report of all config keys with their definition and a "needs-review" flag:

```python
import csv, yaml

# reuse the AnyTagLoader from Level 1 to handle polymorphic details tags
class AnyTagLoader(yaml.FullLoader):
    pass
def construct_unknown(loader, tag_suffix, node):
    if isinstance(node, yaml.MappingNode):
        return loader.construct_mapping(node, deep=True)
    if isinstance(node, yaml.SequenceNode):
        return loader.construct_sequence(node, deep=True)
    return loader.construct_scalar(node)
AnyTagLoader.add_multi_constructor('', construct_unknown)

inv = yaml.load(open("config-inventory.yaml"), Loader=AnyTagLoader)
defined = {i["key"]: i for i in inv["items"] if i["role"] == "DEFINE"}
flagged = {c["key"] for c in inv["checks"]}

with open("config-review.csv", "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["key", "value", "default", "profile", "file", "needs_review"])
    for item in inv["items"]:
        if item["role"] != "READ":
            continue
        d = defined.get(item["key"], {})
        w.writerow([
            item["key"],
            (d.get("value") or {}).get("raw", ""),
            (item.get("defaultValue") or {}).get("raw", ""),
            (item.get("environment") or {}).get("profile", ""),
            (d.get("source") or {}).get("path", ""),
            "yes" if item["key"] in flagged else "no",
        ])
```

Keep the transformer versioned alongside your platform, not inside ConfigRadar — its job is to adapt to *your* format, which ConfigRadar should not know about.

## Level 3 — embed ConfigRadar as a library and add a custom consumer

When you run ConfigRadar inside a Java/JVM tool and want ConfigRadar itself to emit your format, implement the `InventoryConsumer` SPI. This is for embedded use; the standalone CLI currently writes the default YAML only.

### The SPI

```java
package io.github.hzzzzzx.configradar.core.output;

public interface InventoryConsumer {
    String id();                                        // format id, e.g. "my-platform"
    void write(ConfigInventory inventory, OutputStream output) throws IOException;
}
```

`ConfigInventory` is a typed Java record (see `core/model/ConfigInventory.java`) with `items`, `uncertain`, `checks`, `diagnostics`, and `summary`. Implement `write` to serialize it however your platform needs.

### Add the dependency

```xml
<dependency>
  <groupId>io.github.hzzzzzx</groupId>
  <artifactId>config-radar-core</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

(Note: ConfigRadar is not yet published to Maven Central — `0.1.0-SNAPSHOT` must be built locally with `./scripts/build.sh` / `mvn install` first. Publishing is a later milestone.)

### Run a scan in-process and consume with your format

```java
import io.github.hzzzzzx.configradar.core.scan.*;

var input = ScanInput.of(Path.of("/path/to/project"));
var options = ScanOptions.defaults();
var rules = new RuleLoader().load(null);
var result = ScanPipeline.defaults(false).scan(input, options, rules);

// your consumer writes whatever format you need
try (var out = Files.newOutputStream(Path.of("my-platform-format.yaml"))) {
    new MyPlatformConsumer().write(result.inventory(), out);
}
```

The pipeline produces a typed `ConfigInventory`; your consumer is the only thing that changes. This keeps ConfigRadar's scan logic stable while letting each platform own its output shape.

## Choosing a level

| Need | Level |
|---|---|
| CI gate, release review, dashboard reading the inventory | **1** — parse the YAML |
| Load config into an app config center (key/value list, sensitive keys separated) | **1.5** — use the built-in `export` command |
| Your platform needs a different file format than `export` produces | **2** — transform the YAML |
| You embed ConfigRadar inside a JVM tool and want native output | **3** — implement `InventoryConsumer` |

Prefer the lowest level that works. Each higher level adds a dependency on ConfigRadar's internals and a maintenance cost; Level 1 keeps your downstream decoupled from ConfigRadar's Java API entirely.
