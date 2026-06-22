# Codegraph Detector

This document records the optional codegraph integration design.

## Position

`codegraph` is treated as an optional detector backend, not as ConfigRadar's primary model.

```text
ConfigRadar
  -> built-in config file detectors
  -> built-in Java source detectors
  -> optional codegraph-backed detector
  -> ConfigInventory
```

The integration is opt-in:

```bash
config-radar inventory . -o inventory.yml --enable-codegraph
```

If `codegraph` or `sqlite3` is unavailable, the detector reports a warning diagnostic and the scan continues.

## Current Scope

The first implementation only adds one high-value path:

```text
custom annotation
  -> meta-annotated with @Value("${key}")
  -> usage site reads that key
```

Example:

```java
@Value("${payment.timeout}")
public @interface PaymentTimeout {
}

public class PaymentClient {
    @PaymentTimeout
    private int timeout;
}
```

The codegraph detector emits:

```text
ConfigFinding(payment.timeout, READ)
  source = PaymentClient.java
  detectorId = codegraph-config-usage
  details = codegraph/custom-meta-annotation:PaymentTimeout
```

Direct `@Value`, `@ConfigurationProperties`, `@ConditionalOnProperty`, `Environment#getProperty`,
and project rule annotations remain handled by `JavaSourceConfigDetector`.

## Implementation Shape

```text
CodegraphConfigUsageDetector
  -> CodeSemanticProvider
     -> CodegraphCliSemanticProvider
```

`CodegraphCliSemanticProvider` currently:

1. checks `codegraph` and `sqlite3`;
2. runs `codegraph init -i` or `codegraph sync`;
3. reads indexed Java file paths from `.codegraph/codegraph.db`;
4. scans those files for one-layer custom `@Value` meta-annotations;
5. returns ConfigRadar-owned `CodeConfigUsage` records.

The detector converts those records into normal `ConfigFinding` instances.

## Why Not Use codegraph as the Main Engine

`codegraph` is a general code symbol graph. ConfigRadar's primary entities are configuration facts:

```text
ConfigKey
ConfigDefine
ConfigRead
ConfigBinding
Profile
UncertainExpression
```

Keeping an adapter boundary avoids coupling the inventory schema to codegraph's SQLite schema, MCP tools,
watcher, installer, or Node runtime.

## Better Future Shape

The next useful steps, in order:

1. Add an internal `ConfigImpactGraph` sidecar with ConfigRadar-owned node/edge kinds.
2. Let built-in detectors emit graph facts as well as inventory findings.
3. Extend the codegraph provider to import Java symbols and call edges into that graph.
4. Add meta-annotation rules:
   - one-layer `META_ANNOTATED_BY`;
   - later multi-layer chains with a depth cap.
5. Add annotation attribute extraction:
   - `@MyConfig("key")`;
   - `@MyConfig(key = "x", defaultValue = "y")`;
   - constants when they are easy and local.
6. Add impact traversal:
   - `ConfigKey -> Field/Class`;
   - later `Field -> Method -> CALLS`.

Skipped for now:

- MCP integration;
- direct npm embedding;
- Cypher queries;
- reading remote config centers;
- full Java call graph import.
