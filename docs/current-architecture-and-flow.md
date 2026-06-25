# ConfigRadar 当前代码现状与数据流

这份文档用于演示 ConfigRadar 的整体思路：它不是直接把源码文本转成下游格式，而是先把各种配置线索统一成一份稳定的配置事实清单，再让 diff、export、HTML 报告等下游消费这份清单。

## 一句话现状

ConfigRadar 当前已经形成了四层架构：

| 层次 | 人话职责 | 代码入口 |
|---|---|---|
| 输入层 | 接收项目路径、profile、规则、include/exclude 等扫描参数 | `ConfigRadarCli` / `ScanInput` / `ScanOptions` |
| 扫描层 | 静态扫描 Java、Spring 配置文件、部署文件、XML、HOCON、metadata、可选 codegraph 语义索引 | `ScanPipeline` + 多个 `ConfigDetector` |
| 事实模型层 | 把扫描结果变成稳定模型：确定事实进入 `items`，动态或不确定线索进入 `uncertain` | `ConfigFinding` / `UncertainFinding` / `ConfigInventory` |
| 消费层 | 同一份 inventory 可输出 YAML、HTML、app_configs、XAC，或进入 diff | `InventoryConsumer` / `KeyBasedDiffStrategy` / `ConfigDiffCommand` |

## 核心数据流

```mermaid
flowchart LR
  A["项目源码<br/>Java / yml / properties / XML / Docker / k8s"]:::input
  B["文件索引<br/>分类 + include/exclude + 跳过测试目录"]:::scan
  C["Detector 扫描<br/>把源码线索变成 ScanFinding"]:::scan
  D["处理与归一化<br/>key 规范化 + 环境补齐 + 稳定排序"]:::model
  E["Inventory 构建<br/>items 和 uncertain 分流"]:::model
  F["Enricher 增强<br/>敏感键 / 动态键 / 远程配置检查 / summary"]:::model
  G["ConfigInventory YAML<br/>统一事实源"]:::output
  H["HTML 报告"]:::output
  I["export / XAC"]:::output
  J["diff / config-diff"]:::output

  A --> B --> C --> D --> E --> F --> G
  G --> H
  G --> I
  G --> J

  classDef input fill:#dbeafe,stroke:#2563eb,color:#0f172a;
  classDef scan fill:#dcfce7,stroke:#16a34a,color:#0f172a;
  classDef model fill:#fef3c7,stroke:#d97706,color:#0f172a;
  classDef output fill:#ede9fe,stroke:#7c3aed,color:#0f172a;
```

关键点：**ConfigRadar 不 diff 源码，也不 diff HTML/下游格式；它 diff 归一化后的配置事实。**

## 数据转换过程

### 例子 1：`@Value` 注解读取

输入代码：

```java
@Value("${payment.client.timeout:3000}")
private int timeout;
```

转换过程：

```mermaid
flowchart LR
  A["@Value 注解<br/>${payment.client.timeout:3000}"]:::input
  B["Java AST 扫描<br/>识别 Spring 占位符"]:::scan
  C["占位符解析<br/>key = payment.client.timeout<br/>default = 3000"]:::scan
  D["ConfigFinding<br/>role = READ<br/>source = Java 文件行号"]:::fact
  E["BasicFindingNormalizer<br/>normalizedKey = payment.client.timeout"]:::fact
  F["ConfigInventory.items"]:::output

  A --> B --> C --> D --> E --> F

  classDef input fill:#dbeafe,stroke:#2563eb,color:#0f172a;
  classDef scan fill:#dcfce7,stroke:#16a34a,color:#0f172a;
  classDef fact fill:#fef3c7,stroke:#d97706,color:#0f172a;
  classDef output fill:#ede9fe,stroke:#7c3aed,color:#0f172a;
```

对应的事实结构大概是：

```yaml
items:
  - key: payment.client.timeout
    normalizedKey: payment.client.timeout
    role: READ
    value: null
    defaultValue:
      raw: "3000"
      type: NUMBER
    source:
      path: src/main/java/.../PaymentClient.java
      line: 12
      sourceKind: JAVA
    confidence: HIGH
    detectorId: java-source-config
```

### 例子 2：`application.yml` 定义

输入配置：

```yaml
payment:
  client:
    timeout: 5000
```

转换过程：

```mermaid
flowchart LR
  A["application.yml<br/>层级 YAML"]:::input
  B["SpringConfigFileDetector<br/>展开 YAML 路径"]:::scan
  C["扁平 key<br/>payment.client.timeout = 5000"]:::scan
  D["ConfigFinding<br/>role = DEFINE<br/>source = YAML 文件行号"]:::fact
  E["ConfigInventory.items"]:::output

  A --> B --> C --> D --> E

  classDef input fill:#dbeafe,stroke:#2563eb,color:#0f172a;
  classDef scan fill:#dcfce7,stroke:#16a34a,color:#0f172a;
  classDef fact fill:#fef3c7,stroke:#d97706,color:#0f172a;
  classDef output fill:#ede9fe,stroke:#7c3aed,color:#0f172a;
```

这类事实的 `role` 是 `DEFINE`，表示“项目声明了这个配置值”。它和 Java 里的 `READ` 会在同一份 inventory 里并存，所以一个 key 既能看到“哪里定义”，也能看到“哪里读取”。

### 例子 3：动态 key 不猜

输入代码：

```java
environment.getProperty(prefix + ".timeout");
```

如果 `prefix` 无法静态解析，ConfigRadar 不会猜一个 key，而是进入 `uncertain`：

```yaml
uncertain:
  - expression: prefix + ".timeout"
    reason: DYNAMIC_EXPRESSION
    rootSink: Environment.getProperty
    source:
      path: src/main/java/.../PaymentClient.java
      line: 20
    confidence: LOW
```

这个设计的目标是：**确定的配置事实进入 `items`，不确定表达式进入 `uncertain`，避免误报污染主清单。**

## Inventory 的结构

```mermaid
classDiagram
  class ConfigInventory {
    schemaVersion
    project
    summary
    items
    uncertain
    checks
    diagnostics
  }
  class ConfigFinding {
    key
    normalizedKey
    role
    value
    defaultValue
    environment
    source
    confidence
    detectorId
    details
  }
  class UncertainFinding {
    expression
    reason
    rootSink
    environment
    source
    confidence
    detectorId
    details
  }
  class InventoryCheck {
    id
    severity
    message
    key
    source
  }

  ConfigInventory --> ConfigFinding : items
  ConfigInventory --> UncertainFinding : uncertain
  ConfigInventory --> InventoryCheck : checks
```

演示时可以把它翻译成三句话：

- `ConfigFinding`：已经确认 key 的配置事实，比如“定义了 `server.port=8080`”或“代码读取了 `payment.client.timeout`”。
- `UncertainFinding`：有配置访问意图，但 key 是运行时拼出来的，静态阶段不能确定。
- `InventoryCheck`：基于事实自动推出来的审查提示，比如敏感键、动态 key、远程配置中心入口。

## Diff 场景数据流

普通 diff 是两步：先分别扫描两个状态，再比较两份 inventory。

```mermaid
flowchart LR
  A["旧版本源码"]:::input --> B["inventory 扫描"]:::scan --> C["base inventory"]:::model
  D["新版本源码"]:::input --> E["inventory 扫描"]:::scan --> F["head inventory"]:::model
  C --> G["KeyBasedDiffStrategy<br/>identity = normalizedKey + role + profile + region + namespace"]:::diff
  F --> G
  G --> H["ConfigDiff<br/>added / removed / changed / uncertainChanged / checks"]:::output

  classDef input fill:#dbeafe,stroke:#2563eb,color:#0f172a;
  classDef scan fill:#dcfce7,stroke:#16a34a,color:#0f172a;
  classDef model fill:#fef3c7,stroke:#d97706,color:#0f172a;
  classDef diff fill:#fee2e2,stroke:#dc2626,color:#0f172a;
  classDef output fill:#ede9fe,stroke:#7c3aed,color:#0f172a;
```

diff 的身份键目前是：

```text
normalizedKey + role + profile + region + namespace
```

也就是说，`payment.client.timeout` 在 `prod` profile 下的 `DEFINE` 和同一个 key 的 `READ` 是两条不同事实；这样可以分别判断“定义变了”还是“读取变了”。

## `config-diff` 的完整链路

`config-diff` 是给 git 提交流程用的端到端命令。它比普通 diff 多做一步：只保留 git 变更文件相关的配置变化，降低审查噪音。

```mermaid
flowchart LR
  A["用户输入<br/>repo + base-ref + head-ref"]:::user
  B["GitClient<br/>找仓库根目录 + changed files"]:::git
  C["临时 worktree<br/>base ref"]:::git
  D["临时 worktree<br/>head ref"]:::git
  E["扫描 base<br/>生成 base inventory"]:::scan
  F["扫描 head<br/>生成 head inventory"]:::scan
  G["完整 inventory diff"]:::diff
  H["ConfigDiffFilter<br/>只保留 changed files 相关项"]:::diff
  I["changes.yaml<br/>可审查的配置变更"]:::output

  A --> B
  B --> C --> E
  B --> D --> F
  E --> G
  F --> G
  B --> H
  G --> H --> I

  classDef user fill:#dbeafe,stroke:#2563eb,color:#0f172a;
  classDef git fill:#e5e7eb,stroke:#6b7280,color:#0f172a;
  classDef scan fill:#dcfce7,stroke:#16a34a,color:#0f172a;
  classDef diff fill:#fee2e2,stroke:#dc2626,color:#0f172a;
  classDef output fill:#ede9fe,stroke:#7c3aed,color:#0f172a;
```

注意边界：

- `diff` 命令：用户已经有两份 inventory，直接比。
- `config-diff` 命令：用户给两个 git ref，工具自己 checkout 临时 worktree、扫描、diff、过滤。
- 两者最终都落到同一个 `ConfigDiff` 模型。

## HTML 报告在架构里的位置

HTML 报告不是扫描引擎的一部分，它只是一个 `InventoryConsumer`：

```mermaid
flowchart LR
  A["ConfigInventory<br/>统一事实源"]:::model
  B["YamlInventoryConsumer<br/>原生 YAML"]:::output
  C["HtmlReportConsumer<br/>可视化审查报告"]:::output
  D["DefaultFormatConsumer<br/>app_configs"]:::output
  E["XacConsumer<br/>XAC 平台制品"]:::output

  A --> B
  A --> C
  A --> D
  A --> E

  classDef model fill:#fef3c7,stroke:#d97706,color:#0f172a;
  classDef output fill:#ede9fe,stroke:#7c3aed,color:#0f172a;
```

所以报告页的正确定位是：**把 inventory 投影成人能审查的页面**，比如统计卡、分布图、每个 key 的生效值、所有来源弹窗、不确定项和诊断信息。

## 当前扩展点

| 扩展点 | 适合扩展什么 |
|---|---|
| `ConfigDetector` | 新配置来源、新注解、新配置 API、新文件格式 |
| `FindingNormalizer` | key 规范化、环境补齐、稳定排序、别名归一 |
| `InventoryEnricher` | 敏感键、风险评分、owner、远程配置检查、graph sidecar |
| `ConfigDiffStrategy` | 更复杂的 identity、移动检测、影响面 diff |
| `InventoryConsumer` | HTML、CSV、平台 YAML、XAC、CI 审查报告 |
| `config-radar-rules.yaml` | 项目自定义注解、方法调用、配置文件规则 |

## 演示主线

建议按这条线讲：

1. 先展示 `@Value("${payment.client.timeout:3000}")` 和 `application.yml`，说明它们都会变成 `ConfigFinding`。
2. 再展示动态 key，说明不确定的不会猜，而是进入 `uncertain`。
3. 展示 inventory：它是所有下游的统一事实源。
4. 展示 HTML 报告：这是 inventory 的人类审查视图。
5. 展示 diff/config-diff：不是比源码文本，而是比两份配置事实，并且 `config-diff` 会用 git changed files 做降噪。
