# Technical Decisions

This file records decisions that affect implementation direction.

## Accepted

### Project Structure

Decision:

- Use two Maven modules first:
  - `config-radar-core`
  - `config-radar-cli`

Why:

- Keeps the scanning framework usable without the CLI.
- Avoids premature module explosion.

### Runtime JDK

Decision:

- ConfigRadar runs on JDK 21+.
- Scanned projects can still target Java 8 through modern Java where parser support is available.

Why:

- JDK 21 supports records and sealed interfaces used by the core model.
- ConfigRadar itself can use JDK 21 while still scanning projects written for Java 8, 11, 17, or 21.

### CLI Shape

Decision:

- First CLI commands:
  - `config-radar inventory <path> -o config-inventory.yaml`
  - `config-radar diff --base base.yaml --head head.yaml -o config-diff.yaml`
- Keep room for future integration commands such as `profile`, `suggest`, or pack management.

### Minimal Rule File in MVP

Decision:

- MVP supports a local `config-radar-rules.yaml`.
- First rule types:
  - method calls
  - annotations
  - config file patterns

Boundary:

- No remote packs in MVP.
- No plugin classpath loading in MVP.

### Default Output Sections

Decision:

- Default inventory YAML includes:
  - `summary`
  - `items`
  - `uncertain`
  - basic `checks`
- `ruleTuning` is later.

### Test Scope

Decision:

- Test configuration is out of Phase 1 scope.
- Do not scan test sources by default.
- `--include-tests` can be considered later only if a real use case appears.

Why:

- Deployment review should focus on production configuration first.
- Test-only overrides can add noise.
- Current target use case is team development and real runtime configuration changes, not test configuration governance.

### Configuration Values

Decision:

- Output discovered values and default values in Phase 1.
- Keep masking/desensitization as a later capability.

Why:

- Early review needs maximum visibility.
- Masking policy needs separate design to avoid hiding useful evidence.

### Test Fixtures

Decision:

- Add fixture projects for scanner verification.
- Start with `fixtures/spring-basic`.
- Use golden YAML output tests.

Why:

- Scanner behavior needs regression protection.
- Golden files make AI-assisted maintenance safer.

### Rule and Pack Loading

Decision:

- MVP only loads local `config-radar-rules.yaml`.
- Remote packs and plugin classpath loading are later.

### Error Handling

Decision:

- Single detector failure should not fail the whole scan by default.
- Emit diagnostics for detector failures and parse issues.
- Severe input/output failures should return non-zero exit code.

### Logging and Performance Metrics

Decision:

- Add logs for key scan stages.
- Add performance metrics for major pipeline phases.

Key timings:

- file indexing
- resource scanning
- Java/OpenRewrite parsing
- Java detector execution
- finding processing
- normalization
- enrichment
- output writing
- diff calculation

Output:

- human-readable logs
- structured `diagnostics` / `metrics` section in inventory or sidecar when enabled

Default:

- concise logs by default
- verbose diagnostics behind CLI option such as `--verbose` or `--metrics`

### Diff Identity

Decision:

- Use a primary identity for matching config facts:
  - `normalizedKey`
  - `role`
  - `environment`
- Keep source information as evidence, not as the primary identity:
  - source kind
  - source path
  - source line

Why:

- Line numbers change easily and should not turn a changed config into delete plus add.
- Source file movement should be reported as `sourceChanged` or `moved`, not lose the key-level relationship.
- Multiple definitions of the same key can still be represented by source evidence and secondary grouping.

Boundary:

- Do not include line number in identity.
- Use source fields for review evidence and moved/source-changed reporting.

### Config Value Model

Decision:

- Output values and default values with a small typed value model.

Shape:

```java
record ConfigValue(
    String raw,
    Optional<String> normalized,
    Optional<ValueType> type
) {}
```

First value types:

- `string`
- `integer`
- `boolean`
- `duration`
- `placeholder`
- `unknown`

Why:

- Reviewers need the raw value.
- Downstream checks need basic type hints.
- Normalized values can be added when safe.

### Metrics and Diagnostics Output

Decision:

- Default logs stay concise.
- `--metrics <file>` can write performance metrics and diagnostics as a sidecar file.
- Inventory can contain only basic diagnostics unless verbose output is requested.

Why:

- Keeps `config-inventory.yaml` focused on configuration facts.
- Still supports performance and failure analysis when needed.

### OpenRewrite Classpath Strategy

Decision:

- Phase 1 uses best-effort classpath/source-set resolution.
- If full type attribution is unavailable, detectors should degrade to syntax/name-based matching where safe.
- Record classpath/type-attribution issues in diagnostics.

Why:

- Static scanning should not require a successful full project build.
- Many repositories have partial or environment-dependent build setup.

Later:

- Add stronger classpath integration or build-tool-assisted resolution when real projects need it.

### Rule File Schema

Decision:

- MVP rule schema supports `methodCalls`, `annotations`, and `configFiles`.

Example:

```yaml
methodCalls:
  - id: acme-config-get
    owner: com.acme.ConfigCenter
    method: getString
    keyArg: 0
    defaultArg: 1
    confidence: MEDIUM
    role: READ
  - id: acme-config-set
    owner: com.acme.ConfigCenter
    method: set
    keyArg: 0
    valueArg: 1
    confidence: MEDIUM
    role: DEFINE

annotations:
  - id: acme-value
    type: com.acme.ConfigValue
    keyAttribute: value
    defaultAttribute: defaultValue
    confidence: HIGH
    role: READ

configFiles:
  - id: deploy-props
    pattern: deploy/*.properties
    format: PROPERTIES
    scope: RUNTIME
```

Boundary:

- No CEL in the MVP rule schema.
- `keyArg`, `defaultArg`, and `valueArg` are literal argument indexes only; deeper data flow remains a later tracing capability.
- No plugin classpath loading in MVP.

### Use OpenRewrite as the Main Static Scanner

Decision:

- Use OpenRewrite as the main Java/Spring source scanner.

Why:

- Java/Spring semantic scanning needs type-aware source analysis.
- Configuration detection depends on annotations, method calls, YAML/properties, and source locations.

### Phase 1 Focuses on Static Source Scan

Decision:

- Phase 1 scans source and resources without starting the application.
- JAR scan and runtime snapshot stay on the roadmap.

Why:

- This keeps the first implementation useful for PR review and deployment review.
- It avoids runtime environment, credentials, and side-effect problems.

### First Rule Template Types

Decision:

- First custom rule template supports:
  - method calls
  - annotations
  - config file patterns

Why:

- These cover most project-specific wrappers without requiring Java plugins.

### ConfigurationProperties Field-Level Inference

Decision:

- Support field-level key inference for straightforward `@ConfigurationProperties` classes.
- Use one embedded Spring Boot baseline for property-name normalization and relaxed binding concepts.
- Focus on practical compatibility for Spring Boot 2.x and 3.x first.

Why:

- Only recording prefix is too weak for inventory.
- Reimplementing Spring naming rules from scratch is unnecessary and error-prone.
- Spring Boot 2.x and 3.x cover the highest-value target projects.

Boundary:

- Do not start a Spring application.
- Do not require full runtime binding.
- Use static AST/metadata for field discovery.
- Use Spring Boot utilities for canonical property-name handling where feasible.

Version strategy:

1. Depend on one Spring Boot baseline in ConfigRadar, preferably Boot 3.x.
2. Use it for normalized property names and relaxed-binding-style canonicalization.
3. Keep raw keys in all findings.
4. Record the chosen normalization strategy, for example `spring-boot-3-compatible`.
5. Do not implement multi-version adapters until real Boot 2/3 differences require it.

Do not put multiple Spring Boot versions on ConfigRadar's main classpath.

### Schema Versioning

Decision:

- Serialized outputs include schema versions such as `config-inventory/v1` and `config-diff/v1`.
- Additive optional fields are allowed.
- Renaming or changing field meaning requires a schema version bump.

### Evidence in Default Output

Decision:

- Default YAML includes source evidence:
  - source path
  - line when available
  - detector id
  - confidence
  - raw expression or typed details

Why:

- Inventory and diff output must be reviewable.

### Finding Model

Decision:

- Internal pipeline uses `ScanFinding`.
- External inventory output separates confirmed `items` from `uncertain`.

Why:

- The pipeline can process findings uniformly.
- Confirmed config keys and dynamic/uncertain findings remain semantically separate.

### Details Model

Decision:

- Core detectors emit typed detail objects.
- Third-party packs may use controlled `ExternalDetails`.
- Truly unknown cases use `UnknownDetails`.

Why:

- Avoid generic map-based model rot.
- Keep schema self-explaining and AI-maintainable.

### Parallel Scan Strategy

Decision:

- resource files scan by file in parallel
- Java/OpenRewrite scans parse once per module/source set
- Java detectors share parsed OpenRewrite results
- merge results with stable deterministic ordering
- default `parallelism = min(availableProcessors, 8)`
- default `javaParallelism = min(2, parallelism)`

Why:

- resource parsing is cheap and parallelizes well
- Java AST/type parsing is expensive and should not be repeated per detector
- stable output is required for useful YAML diffs
- conservative Java parallelism avoids memory pressure

Stable sort order:

1. source path
2. source line
3. detector id
4. role
5. key or expression
