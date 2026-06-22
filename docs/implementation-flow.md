# Implementation Flow

这份文档描述当前已经落地的“可运行骨架”。它已包含两类真实 detector：Spring `application*.yml/yaml/properties` 配置文件定义扫描，以及 Java 源码中的常见 Spring/Java 配置读取扫描。`config-radar-rules.yaml` 中的 method-call、annotation、configFiles 规则也已经接入扫描。

## Current Goal

当前版本要证明三件事：

- CLI 可以从项目目录跑到 YAML 产物。
- 扫描 pipeline 每个阶段都有明确输入输出。
- detector、normalizer、enricher、diff、consumer 等 Hook 已经嵌入主流程。

## Inventory Flow

```text
CLI inventory
  -> ScanInput
  -> ScanOptions
  -> RuleLoader
  -> ScanPipeline.scan
  -> YamlInventoryConsumer
```

## Pipeline Stages

| Stage | Method | Input | Output | Error behavior |
|---|---|---|---|---|
| File indexing | `FileIndexer.index` | `ScanInput`, `ScanOptions` | `FileIndex` | 项目根目录不可读等结构性错误直接抛出 |
| Detection | `ConfigDetector.detect` | `ScanContext` | `List<ScanFinding>` | 单个 detector 异常转成 diagnostic，扫描继续 |
| Processing | `FindingProcessor.process` | `List<ScanFinding>`, `ScanContext` | `List<ScanFinding>` | 第一版要求确定性，后续可做去重/过滤/补标签 |
| Normalization | `FindingNormalizer.normalize` | `List<ScanFinding>`, `ScanContext` | `List<ScanFinding>` | 必须保留 confirmed 和 uncertain 的语义边界 |
| Inventory build | `InventoryBuilder.build` | `List<ScanFinding>`, `ScanContext` | `ConfigInventory` | 把确定项放入 `items`，动态/不确定项放入 `uncertain` |
| Enrichment | `InventoryEnricher.enrich` | `ConfigInventory`, `ScanContext` | `ConfigInventory` | 补 summary/check/risk 等派生数据 |
| Output | `InventoryConsumer.write` | `ConfigInventory`, `OutputStream` | YAML bytes | 输出 IO 错误直接抛出给 CLI |

## Hook Contract

已进入主流程的 Hook：

| Hook | Interface | 当前实现 | 后续用途 |
|---|---|---|---|
| 文件索引 | `FileIndexer` | `DefaultFileIndexer` | 支持特殊目录、生成文件、自定义配置文件 |
| 配置检测 | `ConfigDetector` | `SpringConfigFileDetector`、`JavaSourceConfigDetector` | Spring、Java、配置中心、三方组件扫描器 |
| 发现处理 | `FindingProcessor` | `NoopFindingProcessor` | 去重、过滤误报、补 detector 标签 |
| 归一化 | `FindingNormalizer` | `BasicFindingNormalizer` | relaxed binding、环境/profile、source 归一化 |
| 清单构建 | `InventoryBuilder` | `DefaultInventoryBuilder` | 固定 public inventory schema |
| 清单增强 | `InventoryEnricher` | `UncertainFindingCheckEnricher`、`SummaryInventoryEnricher` | summary、checks、uncertain 风险等级 |
| 输出消费 | `InventoryConsumer` | `YamlInventoryConsumer` | 默认 YAML，未来接下游平台格式 |
| 差异策略 | `ConfigDiffStrategy` | `KeyBasedDiffStrategy` | 按 `normalizedKey + role + profile` 比较增删改 |

约束：

- Hook 输入输出使用 typed model，不把主数据藏进 `Map<String, Object>`。
- Agent/Skill 类型能力后续只生成建议，真正改变扫描行为要沉淀成规则或 detector。
- detector 未来可以并行，processor/normalizer/enricher 先按配置顺序执行，保证结果稳定。

## Finding Contract

Internal pipeline uses:

```java
ScanFinding
```

Public inventory separates:

```text
items      -> ConfigFinding with required key
uncertain  -> UncertainFinding without guessed key
```

Rules:

- `ConfigFinding.key` and `normalizedKey` are required.
- Dynamic or unresolved keys must use `UncertainFinding`.
- Core detectors should emit typed `FindingDetails`.
- Experimental or third-party packs may emit `ExternalDetails`.
- Repeated `ExternalDetails` should be promoted into typed details.

## Current Runnable Skeleton

Implemented:

- empty pipeline：即使没有真实 detector，也能产出合法空 inventory
- file indexing：扫描项目文件并按类型分类
- Spring config file detector：扫描 `application*.yml/yaml/properties`、`bootstrap*.yml/yaml/properties`、`.env` 定义项和 `${...}` 占位符依赖
- Spring YAML profile support：识别多文档 YAML 中的 `spring.config.activate.on-profile`
- Spring metadata role：`spring.config.import` / `spring.config.activate.*` / `spring.profiles*` / `@Profile` / `@PropertySource` 标记为 `METADATA`
- Java source detector：扫描注解占位符、类/方法级 `@ConfigurationProperties`、`@ConditionalOnProperty`、`@Profile`、`@PropertySource`/`@PropertySources`、`SpringApplication.setDefaultProperties`、`SpringApplicationBuilder.properties`、`Environment.getProperty`、`Environment.getRequiredProperty`、`Environment.containsProperty`、`Binder.get(...).bind`、`System.getProperty`、`System.setProperty`、`System.getenv`、`Integer.getInteger`、`Long.getLong`、`Boolean.getBoolean`
- Rule-driven Java scan：通过 method-call/annotation 规则覆盖项目自定义配置入口
- Rule-driven config file scan：通过 configFiles 规则覆盖自定义 YAML/properties 文件
- Default rules discovery：CLI 未传 `--rules` 时自动读取项目根目录的 `config-radar-rules.yaml`
- Basic key normalizer：归一化大小写、下划线、短横线、驼峰，支撑更稳定的 summary 和 diff
- detector registry：统一注册 detector，后续可扩展成 pack
- pipeline builder：统一组合 detector、processor、normalizer、enricher 等阶段，后续新增组件不需要散落修改构造器参数
- processor/normalizer/enricher hooks：主流程已调用
- uncertain finding checks：动态/无法解析的配置 key 会生成高风险 check
- YAML inventory output：默认下游消费格式
- metrics sidecar output：记录阶段耗时和 diagnostics
- CLI `inventory`：从项目目录生成 inventory
- CLI `diff`：读取两个 inventory，按 key-based 策略输出 `added/removed/changed/uncertainChanged`
- optional codegraph detector：`--enable-codegraph` 可启用外部语义索引，当前只增强自定义 `@Value` meta-annotation 使用点
- detector failure diagnostics：单 detector 失败不会拖垮整次扫描

Not implemented yet:

- full Spring relaxed binding：后续需要完整 Spring 语义时再扩展，当前只做基础 key 归一化
- richer diff identity strategy：后续可加入 namespace、region、类型等维度
- OpenRewrite parsing：后续作为 Java/Spring AST 层能力
- desensitization：后续对敏感 value 做可配置脱敏
- external pack loading：后续加载外部 detector/rule pack
- full Config Impact Graph：后续把 ConfigKey、JavaSymbol、AnnotationUse、CallEdge 变成 ConfigRadar 自己的图 sidecar

## Test Fixture

Current fixture:

```text
fixtures/spring-basic
```

Current tests prove:

- confirmed and uncertain findings are split correctly
- detector failures become diagnostics
- pipeline builder can compose detector/processor/normalizer/enricher stages
- metrics sidecar serializes
- CLI inventory command writes inventory and metrics files
