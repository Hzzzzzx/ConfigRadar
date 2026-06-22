# ConfigRadar Design Draft

This file keeps raw detailed notes from design discussion.

For the organized working plan, see [plan.md](plan.md).

## Positioning

ConfigRadar is not just a config diff tool. It is a configuration inventory foundation with diff and reporting as consumers of the same normalized model.

Primary goals:

- full inventory scan for Java/Spring projects
- optional diff between two inventories
- default YAML output
- extensible detectors for project-specific config patterns
- extensible consumers for future downstream formats
- agent-friendly summaries, checks, and rule suggestions

Non-goal for the first version:

- fully resolving Spring runtime property precedence and final effective values

That can be added later as a `resolve --profile prod` style capability.

## Use Cases

### 1. Full Configuration Inventory

Scan one Java/Spring project and generate a complete configuration inventory.

Questions answered:

- what configuration keys exist
- where each key is defined
- where each key is read
- which keys are tied to Spring profiles or environments
- which keys are only read but not defined
- which keys are defined but not read
- which findings are dynamic or uncertain

Typical output:

- `config-inventory.yaml`
- short human-readable summary
- optional warnings/checks

### 2. Pre-Deployment Configuration Review

Compare two inventories before deployment or code review.

Questions answered:

- which config keys were added
- which config keys were removed
- which values or defaults changed
- which environment/profile-specific values changed
- which code paths started reading new keys
- which changes need manual release confirmation

Typical output:

- `config-diff.yaml`
- release-review summary
- optional risk warnings

### 3. Project-Specific Rule Onboarding

Help a project add custom detection rules when its config style is not covered by built-in detectors.

Examples:

```java
AcmeConfig.get("payment.timeout");
ConfigCenter.get("order-service", "payment.timeout");
@AcmeValue("payment.timeout")
```

Questions answered:

- is this an annotation, method call, config file, metadata, or dynamic key pattern
- can it be expressed as YAML custom rules
- does it need a Java detector plugin
- what confidence level should the rule emit

Typical output:

- custom rule YAML
- one small verification example

### 4. Downstream Consumption

Let other tools consume the same normalized inventory.

First version:

- default YAML consumer only

Future consumers:

- internal deployment platform format
- CI gate format
- owner review CSV
- Markdown release report
- custom YAML schema

Questions answered:

- which stable fields can downstream systems rely on
- how should project-specific output formats be added
- how to keep scan logic separate from output formatting

### 5. Configuration Risk and Quality Checks

Run lightweight checks on inventory or diff output.

Examples:

- `read-but-not-defined`
- `defined-but-not-read`
- `multiple-env-values`
- `dynamic-key`
- `sensitive-looking-key`
- `missing-default`
- `conditional-only`
- `prod-only-change`
- `cross-env-inconsistent`

First version should report warnings, not block by default.

## Core Data Flow

Full scan:

```text
source code / resources
  -> detectors
  -> raw findings
  -> normalize
  -> enrich
  -> ConfigInventory
  -> DefaultYamlConsumer
  -> config-inventory.yaml
```

Diff:

```text
base source
  -> scan
  -> inventory-base.yaml

head source
  -> scan
  -> inventory-head.yaml

inventory-base.yaml + inventory-head.yaml
  -> identity matching
  -> diff strategy
  -> config-diff.yaml
  -> report / downstream consumer
```

The scanner should not diff source text, YAML text, or final reports. It should diff normalized configuration facts.

## Scan Modes

ConfigRadar can evolve across three scan modes. They answer different questions and should not be treated as interchangeable.

### 1. Static Source Scan

Input:

- source code
- resource files

Can see:

- `application.yml` / `application.properties`
- `@Value`
- `@ConfigurationProperties`
- `Environment.getProperty`
- `System.getProperty`
- `@ConditionalOnProperty`
- `@PropertySource`
- test configuration
- comments, Javadocs, and source line numbers

Pros:

- does not start the application
- does not need a real runtime environment
- works well for PR diff, CI, and pre-deployment review
- can report precise source locations
- easy to extend with custom rules
- low risk because it does not execute business code

Cons:

- cannot fully resolve dynamic keys
- depends on type attribution quality
- may miss generated code, Lombok effects, or Kotlin compiler output
- cannot know real runtime values
- cannot know remote config-center final values

Best for:

- full inventory
- source-level diff
- release review
- custom rule onboarding

### 2. Artifact or JAR Scan

Input:

- `target/classes`
- `build/classes`
- normal JAR
- fat JAR

Can see:

- class annotations
- method-call bytecode patterns
- string constants
- packaged `application.yml` / `application.properties`
- `META-INF/spring-configuration-metadata.json`
- dependency metadata and auto-configuration hints

Pros:

- does not require source code
- does not start the application
- closer to the released artifact
- can see generated/compiled results from annotation processors and Lombok
- useful for scanning third-party or already-built packages

Cons:

- source line numbers may be missing or unstable
- comments and Javadocs are mostly gone
- local variable names and parameter names may be missing
- string concatenation is harder to reconstruct
- bytecode-level semantic analysis is harder than source AST analysis
- fat JARs, shading, and obfuscation add complexity

Best for:

- release artifact validation
- scanning packages without source
- verifying what was actually packaged
- reading generated Spring metadata

### 3. Runtime Snapshot Scan

Input:

- running Spring application
- Spring `Environment`
- Actuator endpoint
- optional Java agent or in-process hook

Can see:

- actually active profiles
- actual `PropertySource` order
- final effective values
- environment variables
- Java `-D` system properties
- command-line args
- config server / Nacos / Apollo values after loading
- conditional configuration results
- bound configuration beans

Pros:

- closest to the real runtime state
- can observe final effective config
- can include remote config centers and deployment environment
- can validate runtime-only behavior

Cons:

- requires starting the application or attaching to one
- may have side effects
- slower and less suitable for every PR
- environment-dependent and harder to reproduce
- may not cover code paths that are not exercised
- may expose sensitive production values
- needs masking and access control

Best for:

- production or staging config snapshot
- final environment verification
- incident investigation
- effective-config comparison

### Recommended Evolution

Phase 1:

- static source scan
- default YAML inventory
- inventory diff
- custom rules

Phase 2:

- artifact/JAR scan
- packaged resource scan
- Spring metadata scan

Phase 3:

- optional runtime snapshot
- effective config view
- environment verification

Rule of thumb:

- static scan answers "what the source intends"
- artifact scan answers "what the package contains"
- runtime scan answers "what actually takes effect"

## Detector Layer

Built-in detectors should cover common cases first:

- `System.getProperty("key")`
- `System.getenv("KEY")`
- `Environment.getProperty("key")`
- `@Value("${key:default}")`
- `@ConfigurationProperties(prefix = "prefix")`
- `@ConditionalOnProperty`
- `@PropertySource`
- `application.yml`
- `application-*.yml`
- `application.properties`
- `application-*.properties`
- `bootstrap.yml`
- `bootstrap.properties`
- `spring-configuration-metadata.json`
- `additional-spring-configuration-metadata.json`

Suggested implementation foundation:

- OpenRewrite for Java/Spring AST and structured source traversal
- YAML/properties parser through OpenRewrite or a mature library
- regex only for small inner expressions such as Spring placeholders

## Coverage Candidates

This section lists common configuration sources worth covering. Prefer static facts that can be collected reliably. Runtime-only resolution should be recorded as hints, not guessed.

## Spring Configuration Sources

Spring configuration should be scanned from both sides:

- where configuration values are defined or imported
- where configuration values are consumed by code or annotations

ConfigRadar should collect these facts first. It should not try to fully simulate Spring Boot startup or final property precedence in the first version.

### 1. Spring Boot Default Config Files

Core files:

- `application.properties`
- `application.yml`
- `application.yaml`
- `application-<profile>.properties`
- `application-<profile>.yml`
- `application-<profile>.yaml`

Common default locations:

- `classpath:/`
- `classpath:/config/`
- `file:./`
- `file:./config/`
- `file:./config/*/`

Related control keys:

- `spring.config.name`
- `spring.config.location`
- `spring.config.additional-location`
- `spring.config.import`
- `spring.config.activate.on-profile`
- `spring.profiles.active`
- `spring.profiles.include`

First version behavior:

- parse known config files under project resources and common config directories
- infer profile from `application-<profile>.*`
- record config import/location keys as facts
- do not load remote imports

### 2. Command Line, System Properties, and Environment Variables

Runtime inputs:

- command line args such as `--server.port=8080`
- Java system properties such as `-Dserver.port=8080`
- OS environment variables such as `SERVER_PORT=8080`

Code reads:

- `System.getProperty("server.port")`
- `System.getProperty("server.port", "8080")`
- `System.getenv("SERVER_PORT")`

First version behavior:

- detect literal code reads
- record runtime-input style as source hints if provided as scan input later
- do not require a real deployment environment

### 3. `@PropertySource` and `@PropertySources`

Spring Framework supports adding property resources to the Environment:

```java
@PropertySource("classpath:db.properties")
@PropertySources({
    @PropertySource("classpath:a.properties"),
    @PropertySource("classpath:b.properties")
})
```

First version behavior:

- detect `@PropertySource` and `@PropertySources`
- resolve literal classpath/file locations when they point to local files
- parse properties files when reachable
- mark placeholder locations as uncertain

### 4. Configuration Binding

Common Spring Boot binding patterns:

```java
@ConfigurationProperties(prefix = "payment")
class PaymentProperties {
    private int timeout;
}
```

Related annotations:

- `@ConfigurationProperties(prefix = "payment")`
- `@ConfigurationProperties("payment")`
- `@EnableConfigurationProperties`
- `@ConfigurationPropertiesScan`
- `@ConstructorBinding`
- `@DefaultValue`

First version behavior:

- detect the prefix
- optionally infer child keys from fields when straightforward
- record type and default hints when available
- do not require complete binding semantics

### 5. Placeholder Reads in Annotations

Direct value injection:

```java
@Value("${payment.timeout:3000}")
```

Other common annotation attributes may also contain placeholders:

```java
@Scheduled(cron = "${job.cron}")
@KafkaListener(topics = "${kafka.topic}")
@RequestMapping("${api.prefix}/orders")
```

First version behavior:

- support `@Value` first
- later scan placeholder strings in any annotation attribute
- extract literal `${key}` and `${key:default}`
- mark complex SpEL as uncertain

### 6. Environment and PropertyResolver API Reads

Common reads:

```java
environment.getProperty("payment.timeout");
environment.getProperty("payment.timeout", "3000");
environment.getRequiredProperty("payment.timeout");
propertyResolver.getProperty("payment.timeout");
propertyResolver.getRequiredProperty("payment.timeout");
```

Binder reads:

```java
Binder.get(environment).bind("payment", PaymentProps.class);
```

First version behavior:

- detect literal `getProperty` and `getRequiredProperty` keys
- record default values when provided as literals
- treat `Binder.bind("prefix", ...)` as prefix usage
- mark dynamic key expressions as uncertain

### 7. Programmatic Property Sources

Spring code can add property sources manually:

```java
new MapPropertySource("custom", map);
new PropertiesPropertySource("custom", properties);
environment.getPropertySources().addFirst(...);
environment.getPropertySources().addLast(...);
SpringApplication.setDefaultProperties(...);
new SpringApplicationBuilder(...).properties("a=b");
```

First version behavior:

- detect these APIs as config source mutations
- extract literal `"key=value"` values when present
- extract map literals only when simple
- otherwise emit source mutation as uncertain

### 8. Test-Time Configuration

Test configuration should be included but marked as test scope:

```java
@SpringBootTest(properties = "a=b")
@SpringBootTest(properties = {"a=b", "c=d"})
@TestPropertySource(properties = "a=b")
@TestPropertySource(locations = "classpath:test.properties")
@DynamicPropertySource
DynamicPropertyRegistry.add("a", ...)
TestPropertyValues.of("a=b")
```

First version behavior:

- detect literal test properties
- parse reachable test property files
- mark findings with `scope: test`
- keep test findings separate from production findings in summaries

### 9. Spring Boot Configuration Metadata

Metadata files:

- `META-INF/spring-configuration-metadata.json`
- `META-INF/additional-spring-configuration-metadata.json`

Useful metadata fields:

- key/name
- type
- description
- default value hints
- deprecation info

First version behavior:

- parse metadata files when present
- attach metadata to inventory keys
- do not treat metadata as proof that a key is actively used

### 10. External Imports and Config Centers

Spring Boot can import external configuration:

```properties
spring.config.import=configserver:
spring.config.import=nacos:
spring.config.import=optional:file:./secrets.yml
spring.config.import=classpath:extra.yml
```

First version behavior:

- record import declarations
- parse local classpath/file imports when reachable
- do not fetch remote config centers
- leave remote final values to future optional detectors

### Spring Coverage Plan

P0:

- `application*.yml`
- `application*.yaml`
- `application*.properties`
- `bootstrap*.yml`
- `bootstrap*.yaml`
- `bootstrap*.properties`
- `@Value`
- `@ConfigurationProperties`
- `@ConditionalOnProperty`
- `@PropertySource`
- `Environment.getProperty`
- `Environment.getRequiredProperty`
- `PropertyResolver.getProperty`
- `System.getProperty`
- `System.getenv`
- profile and config import control keys

P1:

- `Binder.bind`
- test-time configuration
- programmatic property sources
- placeholder strings in any annotation attribute
- Spring Boot metadata JSON

P2:

- remote config center final values
- full Spring PropertySource precedence simulation
- final effective config resolution

## Spring Coverage Deep Dive

This section keeps Spring coverage explicit so detector implementation can be added incrementally.

### Spring Core

Concepts and APIs:

- `Environment`
- `ConfigurableEnvironment`
- `PropertySource`
- `MutablePropertySources`
- `PropertyResolver`
- `@PropertySource`
- `@PropertySources`
- placeholder expressions `${key}` and `${key:default}`
- `@Profile`

Static scan targets:

- property reads from `Environment` and `PropertyResolver`
- property source declarations
- local files referenced by `@PropertySource`
- placeholder strings in annotations and resource files
- profile declarations on classes and methods

### Spring Boot External Config

Config files:

- `application.properties`
- `application.yml`
- `application.yaml`
- `application-<profile>.properties`
- `application-<profile>.yml`
- `application-<profile>.yaml`
- `bootstrap.properties`
- `bootstrap.yml`
- `bootstrap.yaml`

Control keys:

- `spring.config.name`
- `spring.config.location`
- `spring.config.additional-location`
- `spring.config.import`
- `spring.config.activate.on-profile`
- `spring.profiles.active`
- `spring.profiles.include`

Other external inputs:

- command line args
- Java system properties
- OS environment variables
- random values such as `random.uuid`
- config tree imports such as `configtree:`

Static scan targets:

- local config files and profile-specific files
- activation documents in YAML
- config import declarations
- code reads of system properties and environment variables
- references to random/configtree values as facts, not final values

### Spring Boot Binding

Binding patterns:

- `@ConfigurationProperties(prefix = "prefix")`
- `@ConfigurationProperties("prefix")`
- `@EnableConfigurationProperties`
- `@ConfigurationPropertiesScan`
- `Binder.bind("prefix", ...)`
- constructor binding
- nested property classes
- list/map binding
- `@DefaultValue`
- configuration metadata JSON

Static scan targets:

- prefixes
- straightforward child keys from fields and records
- nested property structure when simple
- list/map fields as grouped keys or prefix facts
- metadata type/description/deprecation/default hints

Do not require complete Spring binder semantics in the first version.

### Spring Conditions

Condition patterns:

- `@ConditionalOnProperty`
- `@ConditionalOnExpression`
- `@Profile`
- auto-configuration conditions

Static scan targets:

- property names and prefixes from `@ConditionalOnProperty`
- expected values from `havingValue`
- `matchIfMissing`
- profile constraints
- placeholder expressions inside condition expressions when simple

Treat `@ConditionalOnExpression` as lower confidence unless it contains clear placeholders.

### Spring Test Config

Test sources:

- `@SpringBootTest(properties = "...")`
- `@TestPropertySource(properties = "...")`
- `@TestPropertySource(locations = "...")`
- `@DynamicPropertySource`
- `DynamicPropertyRegistry.add("key", ...)`
- `TestPropertyValues.of("key=value")`

Static scan targets:

- literal test properties
- local test property files
- dynamic registry keys
- scope marker `test`

### Spring Ecosystem Annotation Placeholders

Common annotations that may contain placeholders:

- `@Scheduled(cron = "${job.cron}")`
- `@KafkaListener(topics = "${kafka.topic}")`
- `@RabbitListener(queues = "${rabbit.queue}")`
- `@JmsListener(destination = "${jms.destination}")`
- `@FeignClient(url = "${service.url}")`
- `@RequestMapping("${api.prefix}/orders")`
- `@Cacheable(cacheNames = "${cache.name}")`
- `@Retryable`
- `@CircuitBreaker`
- `@RateLimiter`
- `@Bulkhead`
- `@TimeLimiter`

Static scan target:

- any annotation attribute string containing `${...}`

This generalized placeholder detector is more valuable than maintaining a long annotation allowlist.

## Config Center Coverage

Config centers should be treated as optional external sources. Static scan should identify entry points, namespaces, groups, data IDs, keys, listeners, and imports. It should not fetch remote values in the first version.

### Spring Cloud Config

Static scan targets:

- `spring.config.import=configserver:`
- legacy `bootstrap.yml` / `bootstrap.properties`
- `spring.cloud.config.*`
- `@RefreshScope`
- refresh-related dependencies or annotations as hints

Collected facts:

- config server import exists
- application/profile/label hints
- refreshable bean scope
- local fallback values

### Apollo

Static scan targets:

- `ConfigService.getAppConfig()`
- `ConfigService.getConfig("namespace")`
- `config.getProperty("key", defaultValue)`
- `@ApolloConfig`
- `@ApolloConfigChangeListener`
- Apollo namespace properties
- `@Value` placeholders used with Apollo-backed Spring Environment

Collected facts:

- namespace
- key
- default value
- listener/change callback
- whether a key is read directly or via Spring placeholder

### Nacos

Static scan targets:

- `spring.config.import=nacos:`
- `spring.cloud.nacos.config.*`
- Nacos `ConfigService.getConfig(...)`
- `dataId`
- `group`
- namespace
- `@NacosValue` when present
- listener APIs

Collected facts:

- dataId
- group
- namespace
- key if directly readable
- listener/change callback
- local fallback values

### Consul, Etcd, and Other Stores

Static scan targets:

- Spring Cloud Consul config properties
- custom etcd client reads
- key-value path strings
- listener/watch APIs

Collected facts:

- backend type
- path/key prefix
- watch/listener usage
- uncertain dynamic access when key paths are built at runtime

### Archaius and Dynamic Config Libraries

Static scan targets:

- `DynamicPropertyFactory.getInstance().getStringProperty("key", defaultValue)`
- `getIntProperty`
- `getBooleanProperty`
- `DynamicStringProperty`
- `DynamicIntProperty`
- `DynamicBooleanProperty`

Collected facts:

- key
- default value
- value type
- dynamic property type

### Custom Config Centers

Common patterns:

- `ConfigCenter.get("key")`
- `ConfigClient.getString("key", defaultValue)`
- `ConfigClient.get("namespace", "key")`
- `@ConfigValue("key")`
- change listeners or callbacks

Static strategy:

- use project profiling to discover wrappers
- generate `config-radar-rules.yaml`
- let users review and refine generated rules
- keep complex cases as uncertain until confirmed

### Config Center Boundary

Static scan should collect:

- imports
- namespaces
- groups
- data IDs
- direct keys
- default values
- listeners
- local fallback files
- dynamic/uncertain access patterns

Static scan should not:

- fetch remote values by default
- require credentials
- treat remote values as local source truth
- expose secrets

Runtime snapshot mode can later add connector-based value collection with masking and access control.

## Common Third-Party Component Coverage

Phase 1 should cover common Java/Spring ecosystem components through static configuration files, Spring property prefixes, annotation placeholders, and XML/YAML placeholders.

The goal is not to fully model every third-party schema. The goal is to find configuration keys, source locations, environment dimensions, placeholders, and risky changes.

### Data Stores

Relational databases:

- MySQL
- PostgreSQL
- Oracle
- SQL Server
- MariaDB
- H2

Common Spring keys:

- `spring.datasource.*`
- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.datasource.driver-class-name`
- `spring.jpa.*`
- `spring.jpa.hibernate.*`
- `spring.sql.init.*`
- `spring.flyway.*`
- `spring.liquibase.*`

Connection pools:

- HikariCP: `spring.datasource.hikari.*`
- Druid: `spring.datasource.druid.*`
- Tomcat JDBC: `spring.datasource.tomcat.*`

Static scan targets:

- config files
- JDBC URLs
- usernames/password key names as sensitive-looking keys
- migration tool config
- pool size/timeouts

### Redis and Cache

Common components:

- Spring Data Redis
- Lettuce
- Jedis
- Redisson
- Caffeine
- Ehcache

Common Spring keys:

- `spring.data.redis.*`
- `spring.redis.*`
- `spring.cache.*`
- `spring.cache.redis.*`
- `spring.cache.caffeine.*`
- `spring.cache.ehcache.*`

Redisson files:

- `redisson.yml`
- `redisson.yaml`
- `redisson.json`
- `redisson.conf`

Static scan targets:

- Redis host/port/database/password keys
- Redisson config files
- cache names and TTL values
- placeholders inside Redisson/Ehcache config

### Messaging and Streaming

Kafka:

- `spring.kafka.*`
- `spring.kafka.bootstrap-servers`
- `spring.kafka.consumer.*`
- `spring.kafka.producer.*`
- `spring.kafka.streams.*`
- `@KafkaListener(topics = "${...}")`

RabbitMQ:

- `spring.rabbitmq.*`
- `@RabbitListener(queues = "${...}")`
- `@Queue("${...}")`
- `@Exchange("${...}")`

JMS/ActiveMQ/Artemis:

- `spring.jms.*`
- `spring.activemq.*`
- `spring.artemis.*`
- `@JmsListener(destination = "${...}")`

RocketMQ/Pulsar, when dependencies are present:

- `rocketmq.*`
- `pulsar.*`
- listener annotations containing `${...}`

Static scan targets:

- broker addresses
- topic/queue/exchange names
- consumer group IDs
- client IDs
- listener annotation placeholders
- retry/concurrency/batch settings

### HTTP Clients and Service Calls

Common components:

- OpenFeign
- RestTemplate
- WebClient
- OkHttp
- Apache HttpClient

Spring/annotation targets:

- `@FeignClient(name = "...", url = "${...}")`
- `spring.cloud.openfeign.*`
- `feign.*`
- `service.*.url`
- `*.endpoint`
- `*.base-url`
- `*.timeout`

Static scan targets:

- URL/base-url/endpoint keys
- timeout/retry keys
- placeholders in client annotations
- literal service endpoint property reads

### Scheduling, Jobs, and Workflow

Common components:

- Spring Scheduling
- Quartz
- XXL-JOB
- ElasticJob

Common keys/files:

- `@Scheduled(cron = "${...}")`
- `spring.quartz.*`
- `quartz.properties`
- `xxl.job.*`
- `elasticjob.*`

Static scan targets:

- cron expressions
- job enable flags
- executor addresses
- namespace/app names
- registry addresses

### Logging and Observability

Logging:

- Logback
- Log4j2

Files:

- `logback-spring.xml`
- `logback.xml`
- `log4j2-spring.xml`
- `log4j2.xml`

Common keys:

- `logging.*`
- `logging.level.*`
- `logging.file.*`
- `logging.pattern.*`

Observability:

- Actuator: `management.*`
- Micrometer: `management.metrics.*`
- Prometheus: `management.prometheus.*`
- Tracing: `management.tracing.*`
- Zipkin: `management.zipkin.*`
- OpenTelemetry: `otel.*`
- Sentry: `sentry.*`

Static scan targets:

- placeholders in logging XML
- log levels
- file paths
- metrics/tracing endpoints
- tokens/DSN-looking sensitive keys

### Security and Auth

Common components:

- Spring Security
- OAuth2 Client
- Resource Server
- JWT
- LDAP

Common keys:

- `spring.security.*`
- `spring.security.oauth2.client.*`
- `spring.security.oauth2.resourceserver.*`
- `spring.ldap.*`
- `jwt.*`
- `auth.*`

Static scan targets:

- issuer URI
- JWK set URI
- client ID
- client secret key names
- token endpoints
- LDAP URLs

### Cloud and Storage

AWS:

- `aws.*`
- `cloud.aws.*`
- `spring.cloud.aws.*`

Object storage:

- S3
- OSS
- COS
- MinIO

Common keys:

- `s3.*`
- `oss.*`
- `cos.*`
- `minio.*`
- `storage.*`

Static scan targets:

- endpoint
- region
- bucket
- access key / secret key names
- path prefix

### Search, Index, and Analytics

Common components:

- Elasticsearch
- OpenSearch
- Solr

Common keys:

- `spring.elasticsearch.*`
- `spring.data.elasticsearch.*`
- `elasticsearch.*`
- `opensearch.*`
- `solr.*`

Static scan targets:

- hosts/URIs
- index names
- username/password key names
- timeouts

### RPC, Service Discovery, and Gateway

Common components:

- Dubbo
- gRPC
- Spring Cloud Gateway
- Eureka
- Consul
- Zookeeper

Common keys:

- `dubbo.*`
- `grpc.*`
- `spring.cloud.gateway.*`
- `eureka.*`
- `spring.cloud.consul.*`
- `zookeeper.*`

Static scan targets:

- registry addresses
- service names
- route predicates/filters
- gateway target URIs
- placeholders in route definitions

### Rate Limit, Resilience, and Feature Flags

Common components:

- Resilience4j
- Sentinel
- Bucket4j
- Togglz
- FF4J
- Unleash

Common keys:

- `resilience4j.*`
- `spring.cloud.circuitbreaker.*`
- `sentinel.*`
- `bucket4j.*`
- `togglz.*`
- `ff4j.*`
- `unleash.*`
- `feature.*`

Static scan targets:

- circuit breaker names
- retry/time limiter settings
- rate limit config
- feature flag keys
- annotation placeholders

### Template Strategy

For third-party components, Phase 1 should use broad static tactics:

- parse common config files
- collect known Spring property prefixes
- scan all annotation attributes for `${...}`
- scan XML/YAML/properties placeholders
- classify sensitive-looking keys
- mark component/source type when known

Avoid full per-component schema modeling until a real use case needs it.

### P0: Common and Easy to Implement

Spring config files:

- `application.yml`
- `application.yaml`
- `application.properties`
- `application-*.yml`
- `application-*.yaml`
- `application-*.properties`
- `bootstrap.yml`
- `bootstrap.yaml`
- `bootstrap.properties`
- `bootstrap-*.yml`
- `bootstrap-*.yaml`
- `bootstrap-*.properties`

Common YAML/properties keys with environment meaning:

- `spring.profiles.active`
- `spring.profiles.include`
- `spring.config.activate.on-profile`
- `spring.config.import`
- `spring.config.location`
- `spring.config.additional-location`

Spring annotation reads and conditions:

- `@Value("${key}")`
- `@Value("${key:default}")`
- `@ConfigurationProperties(prefix = "prefix")`
- `@ConfigurationProperties("prefix")`
- `@ConditionalOnProperty(name = "key")`
- `@ConditionalOnProperty(value = "key")`
- `@ConditionalOnProperty(prefix = "prefix", name = "key")`
- `@ConditionalOnProperty(prefix = "prefix", name = {"a", "b"})`
- `@PropertySource("classpath:foo.properties")`
- `@PropertySources`
- `@Profile("prod")`

Java and Spring API reads:

- `System.getProperty("key")`
- `System.getProperty("key", "default")`
- `System.getenv("KEY")`
- `Environment.getProperty("key")`
- `Environment.getProperty("key", "default")`
- `Environment.getRequiredProperty("key")`
- `ConfigurableEnvironment.getProperty("key")`
- `PropertyResolver.getProperty("key")`
- `PropertyResolver.getRequiredProperty("key")`

Spring placeholder strings in common files:

- `${key}` in `.yml`, `.yaml`, `.properties`
- `${key:default}` in `.yml`, `.yaml`, `.properties`

### P1: High Value and Still Reasonable

Spring Boot binding and metadata:

- `Binder.get(environment).bind("prefix", ...)`
- `Binder.bind("prefix", ...)`
- `@ConstructorBinding`
- `@DefaultValue`
- `spring-configuration-metadata.json`
- `additional-spring-configuration-metadata.json`

Test-time configuration:

- `@SpringBootTest(properties = "key=value")`
- `@SpringBootTest(properties = {"a=b", "c=d"})`
- `@TestPropertySource(properties = "key=value")`
- `@TestPropertySource(properties = {"a=b", "c=d"})`
- `@TestPropertySource(locations = "classpath:test.properties")`
- `@DynamicPropertySource`
- `DynamicPropertyRegistry.add("key", ...)`
- `TestPropertyValues.of("key=value")`

Spring programmatic property sources:

- `new MapPropertySource("name", map)`
- `new PropertiesPropertySource("name", properties)`
- `environment.getPropertySources().addFirst(...)`
- `environment.getPropertySources().addLast(...)`
- `SpringApplication.setDefaultProperties(...)`
- `new SpringApplicationBuilder(...).properties("key=value")`

Framework config files:

- `logback-spring.xml`
- `logback.xml`
- `log4j2-spring.xml`
- `log4j2.xml`
- `ehcache.xml`
- `quartz.properties`

XML and template placeholders:

- `${key}` in Spring XML
- `${key:default}` in Spring XML
- `${key}` in logging XML
- `${key}` in resource templates

Build and packaged config hints:

- Maven resource files under `src/main/resources`
- Gradle resource files under `src/main/resources`
- filtered placeholders such as `@key@`
- filtered placeholders such as `${key}`

### P2: Useful but Better as Optional Detectors or Custom Rules

Common config-center or dynamic-config libraries:

- Apollo `ConfigService.getConfig(...).getProperty("key", defaultValue)`
- Apollo `ConfigService.getAppConfig().getProperty("key", defaultValue)`
- Nacos config client usage
- Netflix Archaius `DynamicPropertyFactory.getInstance().getStringProperty("key", defaultValue)`
- MicroProfile Config `ConfigProvider.getConfig().getValue("key", Type.class)`
- Typesafe Config `config.getString("key")`
- Apache Commons Configuration `configuration.getString("key")`
- Owner config interfaces

Common cloud and container config files:

- `Dockerfile` `ENV`
- `docker-compose.yml` `environment`
- Kubernetes `ConfigMap`
- Kubernetes `Secret`
- Kubernetes `env`
- Kubernetes `envFrom`
- Helm `values.yaml`
- Helm templates containing config env vars

These should be treated carefully. They often describe deployment inputs, not direct application keys.

Custom project patterns:

- `AcmeConfig.get("key")`
- `ConfigCenter.get("namespace", "key")`
- `ConfigClient.getString("key", defaultValue)`
- `@AcmeValue("key")`
- `@ConfigKey("key")`
- enum constants used as config keys
- static final constants used as config keys

Dynamic or low-confidence patterns:

- `env.getProperty(prefix + ".timeout")`
- `System.getProperty(serviceName + ".enabled")`
- `config.getString(keyFromRequest)`
- keys assembled from enums, tenant names, or runtime state

These should be emitted as `uncertain`, not exact added/removed/changed keys.

## Raw Finding

Detectors should emit raw findings before any heavy merging.

Example:

```yaml
rawKey: payment.timeout
rawValue: "3000"
role: define
source:
  path: src/main/resources/application-prod.yml
  line: 12
  format: yaml
detector: spring-yaml
confidence: high
rawExpression: null
```

Confidence levels:

- `high`: literal key or direct structured config key
- `medium`: simple constant or lightly derived key
- `low`: dynamic expression or incomplete context

Dynamic keys should not be guessed into exact keys.

## Inventory Model

The inventory is the stable intermediate model. Downstream outputs should render from this model.

Default YAML shape:

```yaml
schemaVersion: config-inventory/v1
project:
  name: order-service
  ref: 8f3a2c1

summary:
  keys: 42
  definitions: 58
  reads: 37
  conditions: 6
  uncertain: 4

items:
  - id: sha256(...)
    key: payment.timeout
    normalizedKey: payment.timeout
    role: define
    sourceType: yaml
    source:
      path: src/main/resources/application-prod.yml
      line: 12
      format: yaml
    value:
      raw: "3000"
      type: integer
    defaultValue: null
    environment:
      profile: prod
      region: null
      namespace: null
    usage:
      framework: spring
    confidence: high
    detector: spring-yaml
    tags: [timeout]

keys:
  - key: payment.timeout
    definitions:
      - value: "3000"
        profile: prod
        source: src/main/resources/application-prod.yml:12
        detector: spring-yaml
        confidence: high
    reads:
      - defaultValue: "3000"
        source: src/main/java/com/acme/PaymentClient.java:18
        detector: spring-value
        confidence: high
    conditions: []
    metadata:
      type: integer
      description: Payment timeout in milliseconds
    riskTags: [timeout]

uncertain:
  - expression: env.getProperty(prefix + ".timeout")
    source: src/main/java/com/acme/DynamicConfig.java:42
    detector: environment-get-property
    confidence: low
    reason: dynamic-key
```

Keep both views:

- `items`: atomic facts for machines
- `keys`: grouped view for humans

## Normalization

Normalize only when the rule is explicit.

Examples:

- Spring relaxed binding may relate `payment.timeout`, `payment-timeout`, and `PAYMENT_TIMEOUT`
- environment variables may need a separate normalized form
- custom projects may define their own key conventions

Do not silently collapse unrelated keys.

## Environment Context

First version should collect environment facts, not compute final runtime values.

Examples:

- `application.yml` -> default profile
- `application-prod.yml` -> `prod`
- `@Profile("prod")` -> `prod`
- `@ConditionalOnProperty` -> condition fact
- deployment-provided `-Dkey=value` can be added later as an input source

## Checks

Full inventory can include non-blocking checks:

- `defined-but-not-read`
- `read-but-not-defined`
- `multiple-env-values`
- `dynamic-key`
- `sensitive-looking-key`
- `missing-default`
- `conditional-only`

Example:

```yaml
checks:
  - type: read-but-not-defined
    key: payment.endpoint
    severity: warning
    source: src/main/java/com/acme/PaymentClient.java:22

  - type: sensitive-looking-key
    key: db.password
    severity: info
    source: src/main/resources/application-prod.yml:8
```

## Uncertain and Dynamic Configuration

Dynamic configuration reads should be first-class findings.

ConfigRadar should not guess exact keys from complex dynamic expressions. It should expose them, classify them, summarize them, and track whether they grow over time.

Example uncertain item:

```yaml
uncertain:
  - id: sha256(...)
    category: dynamic-key
    expression: env.getProperty(prefix + ".timeout")
    source: src/main/java/com/acme/DynamicConfig.java:42
    rootSink: Environment.getProperty
    wrapper: null
    reason: string-concat
    knownParts:
      suffix: .timeout
    confidence: low
    suggestedAction: add-prefix-rule-or-review
```

Suggested categories:

- `string-concat`
- `variable-key`
- `method-return-key`
- `map-driven-key`
- `enum-driven-key`
- `tenant-or-region-key`
- `unknown-wrapper`
- `remote-config-access`

Inventory summary:

```yaml
uncertainSummary:
  total: 12
  byReason:
    string-concat: 5
    variable-key: 3
    method-return-key: 2
    map-driven-key: 1
    unknown-wrapper: 1
  byRootSink:
    Environment.getProperty: 7
    System.getProperty: 3
    ConfigCenter.get: 2
  byPackage:
    com.acme.payment: 6
    com.acme.common.config: 4
    com.acme.order: 2
```

Diff should track uncertain changes:

```yaml
uncertainDiff:
  added: 3
  removed: 1
  changedPatterns: 1
  addedItems:
    - category: dynamic-key
      reason: string-concat
      expression: env.getProperty(prefix + ".timeout")
      source: src/main/java/com/acme/PaymentConfig.java:42
      risk: high
```

Risk rules:

- new dynamic config read in diff: `high`
- dynamic config count increases above threshold: `high`
- dynamic config concentrated in deployment-critical package: `high`
- existing stable dynamic pattern with reviewed rule suggestion: `medium`
- dynamic read in test scope only: `low`

This lets ConfigRadar report risk without pretending to know the final key.

Future runtime snapshot mode can use these uncertain findings as targets:

- confirm which dynamic expressions are actually executed
- capture resolved keys when the application reads them
- capture effective values with masking
- map dynamic reads back to source locations
- reduce repeated uncertain findings after user review

Static scan should expose the uncertainty. Runtime scan can later validate or complete it.

## Diff Design

Diff should compare two inventories.

Recommended layers:

1. key diff: added or removed configuration keys
2. definition diff: value, default value, profile, source, type
3. usage diff: reads, conditions, usage style

Identity should be stable across line movement:

```text
identity = normalizedKey + role + profile + sourceScope
```

Do not include line number in the default identity.

Built-in diff modes:

- `key`: compare only key sets
- `full`: compare keys, values, defaults, profiles, usage, and source movement
- `env`: run full diff grouped by environment/profile

Default diff output:

```yaml
schemaVersion: config-diff/v1
base:
  ref: main
head:
  ref: feature/foo

summary:
  addedKeys: 2
  removedKeys: 1
  changedDefinitions: 3
  changedUsages: 4
  uncertain: 2

added:
  - key: payment.retry.enabled
    role: define
    profile: prod
    value: "true"
    source: src/main/resources/application-prod.yml:20
    confidence: high

removed:
  - key: legacy.payment.enabled
    role: define
    profile: prod
    oldValue: "true"
    oldSource: src/main/resources/application-prod.yml:31
    confidence: high

changed:
  - key: payment.timeout
    role: define
    profile: prod
    changes:
      value:
        old: "3000"
        new: "5000"
      source:
        old: src/main/resources/application-prod.yml:12
        new: src/main/resources/application-prod.yml:12
    confidence: high

usageChanged:
  - key: payment.timeout
    change: added-read
    source: src/main/java/com/acme/PaymentClient.java:18
    detector: spring-value

uncertain:
  - expression: env.getProperty(prefix + ".timeout")
    source: src/main/java/com/acme/DynamicConfig.java:42
    reason: dynamic-key
```

## Extension Points

Keep extension points thin.

```java
interface ConfigDetector {
    List<ConfigItem> detect(ScanContext context);
}

interface ConfigConsumer {
    String name();
    void write(ConfigInventory inventory, OutputStream out);
}

interface ConfigDiffStrategy {
    String name();
    ConfigDiff diff(ConfigInventory base, ConfigInventory head, DiffOptions options);
}
```

First version should only implement:

- built-in Java/Spring/resource detectors
- default YAML consumer
- `key`, `full`, and `env` diff strategies

Future consumers can be added for internal deployment platforms, CI gates, owner review CSV, Markdown reports, or custom YAML schemas.

## Project Profiling and Rule Evolution

ConfigRadar should support a two-stage workflow:

1. profile a project once with deeper tracing
2. use the generated rule template for fast daily scans

The goal is to turn project-specific configuration knowledge into a reviewable asset, not Java code.

```text
project source
  -> deep trace profiler
  -> config-radar-rules.yaml
  -> inventory / diff scans
  -> uncertain findings
  -> agent suggestions
  -> reviewed rule updates
```

### Profiling Stage

The profiler starts from stable configuration roots:

- `Environment.getProperty`
- `PropertyResolver.getProperty`
- `System.getProperty`
- `System.getenv`
- `Binder.bind`
- Spring placeholders such as `${key}`
- known `PropertySource` creation APIs

It then finds simple project wrappers:

- one-hop method delegation
- simple `static final String` constants
- simple string prefix composition
- wrapper methods such as `ConfigCenter.getString(key, defaultValue)`
- wrapper annotations such as `@AcmeValue("key")`

The profiler should emit candidate rules with evidence, not silently change scan behavior.

### Rule Template

Default template file:

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
    defaultArg: null
    confidence: high
    evidence:
      - com.acme.Configs#get -> Environment#getProperty

  - id: feature-flag-enabled
    owner: com.acme.FeatureFlag
    method: enabled
    keyArg: 0
    keyPrefix: feature.
    confidence: medium
    evidence:
      - FeatureFlag#enabled -> Environment#getProperty("feature." + key)

annotations:
  - id: acme-value
    type: com.acme.config.AcmeValue
    keyAttribute: value
    confidence: high

configFiles:
  - id: deploy-properties
    pattern: deploy/*.properties
    format: properties
    scope: deployment
```

Template principles:

- prefer declarative YAML rules over Java extensions
- include evidence for generated rules
- keep confidence explicit
- allow users to delete or edit rules in review
- treat the template as a project asset that can be committed

### Daily Scan Stage

Daily scans should use:

- built-in detectors
- checked-in `config-radar-rules.yaml`

They should not run full-project deep tracing by default.

```bash
config-radar inventory . --rules config-radar-rules.yaml -o inventory.yaml
config-radar diff --base base.yaml --head head.yaml -o diff.yaml
```

### Agent Fallback

Diff scenarios are good fallback targets because the changed code is usually small.

The agent can inspect:

- changed files
- uncertain findings
- new string literals that look like config keys
- new wrapper methods
- calls near known config roots

The agent should output suggestions, not directly mutate inventory:

```yaml
suggestions:
  - type: add-method-rule
    reason: ConfigCenter.getString delegates to Environment.getProperty
    rule:
      owner: com.acme.ConfigCenter
      method: getString
      keyArg: 0
      defaultArg: 1
      confidence: medium
    evidence:
      - src/main/java/com/acme/ConfigCenter.java:18
      - src/main/java/com/acme/PaymentClient.java:42
```

After review, suggestions can be applied to the rule template.

### CEL-Guided Tracing

CEL can be used later to make profiling configurable without Java code.

Example use cases:

- include only methods that look like config accessors
- stop tracing at package boundaries
- mark specific owner/method patterns as candidate config reads
- limit trace depth per package

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

CEL should guide profiling and filtering. It should not replace the normalized rule template.

## Custom Rules

Project-specific patterns should be easy to describe without Java code.

Example:

```yaml
annotations:
  - type: com.acme.config.AcmeValue
    keyAttribute: value
    confidence: high

methodCalls:
  - owner: com.acme.config.AcmeConfig
    method: get
    keyArg: 0
    confidence: high

  - owner: com.acme.config.ConfigCenter
    method: get
    namespaceArg: 0
    keyArg: 1
    confidence: medium

configFiles:
  - conf/**/*.yaml
  - deploy/*.properties
```

Use Java plugins only when:

- key extraction depends on custom AST logic
- multiple arguments must be combined
- constants or wrappers require semantic resolution
- YAML rules cannot express the case

## Agent Skills

Two companion skills make sense.

### config-inventory-scanner

Use for:

- running or explaining full scans
- running or explaining diffs
- reading inventory/diff YAML
- summarizing deployment risk
- suggesting missing custom rules
- translating machine output into release-review language

### config-scanner-extension

Use for:

- helping users add project-specific detectors
- classifying examples as annotation, method call, config file, metadata, or dynamic key
- generating minimal custom rule YAML
- deciding when a Java detector plugin is necessary

## CLI Sketch

Keep commands boring:

```bash
config-radar inventory . -o config-inventory.yaml

config-radar diff \
  --base inventory-main.yaml \
  --head inventory-feature.yaml \
  --mode full \
  -o config-diff.yaml

config-radar inventory . \
  --rules config-radar-rules.yaml \
  --consumer yaml \
  -o config-inventory.yaml
```

Possible later command:

```bash
config-radar resolve inventory.yaml --profile prod -o effective-config.yaml
```

## First Milestone

Phase 1 should be useful for most Java/Spring projects without custom code.

### Phase 1 Must

Core static inventory:

1. scan Spring config files:
   - `application*.yml`
   - `application*.yaml`
   - `application*.properties`
   - `bootstrap*.yml`
   - `bootstrap*.yaml`
   - `bootstrap*.properties`
2. scan Java/Spring direct reads:
   - `System.getProperty`
   - `System.getenv`
   - `Environment.getProperty`
   - `Environment.getRequiredProperty`
   - `PropertyResolver.getProperty`
   - `PropertyResolver.getRequiredProperty`
3. scan Spring annotations:
   - `@Value`
   - `@ConfigurationProperties`
   - `@ConditionalOnProperty`
   - `@PropertySource`
   - `@Profile`
4. scan placeholders:
   - `${key}`
   - `${key:default}`
   - annotation attributes
   - YAML/properties/XML files
5. emit:
   - `config-inventory.yaml`
   - `uncertain` findings
   - `uncertainSummary`
6. diff two inventories:
   - `key` mode
   - `full` mode
7. support default YAML output.

### Phase 1 Should

High-value additions if implementation stays simple:

1. static final string constants
2. simple string prefix composition
3. Spring Boot metadata JSON
4. test-time config:
   - `@SpringBootTest(properties)`
   - `@TestPropertySource`
   - `DynamicPropertyRegistry.add`
5. common ecosystem files:
   - `logback-spring.xml`
   - `log4j2-spring.xml`
   - `redisson.yml`
   - `redisson.yaml`
   - `quartz.properties`
   - MyBatis XML placeholders
6. known component prefixes:
   - datasource/JPA/Flyway/Liquibase
   - Redis/cache/Redisson
   - Kafka/Rabbit/JMS
   - Feign/Gateway
   - Actuator/observability
   - security/OAuth2/JWT
7. simple custom rule YAML:
   - method calls
   - annotations
   - config file patterns

### Phase 1 Later or Optional

Keep these as explicit extension points, not initial blockers:

1. project profiling and generated rules
2. CEL-guided tracing
3. artifact/JAR scan
4. runtime snapshot
5. remote config center value fetching
6. complete Spring PropertySource precedence simulation
7. many downstream consumers
8. complex ownership inference
9. broad security policy engine
10. deep per-component schema modeling

### Priority Rule

Implement broad, reliable static coverage before deep component-specific semantics.

First target:

```text
Java Core + Spring Core + generic placeholders + common config files
```

Then add ecosystem coverage only where it can reuse the same parser and placeholder detection.

## Rule Enablement and Skill-Guided Tuning

Early versions should default to broad scanning for core Java/Spring rules. This makes first adoption simple and avoids requiring users to know their project's configuration style upfront.

As detector packs and custom rules grow, ConfigRadar should help users tune rule enablement after observing scan results.

Default behavior:

- core Java rules enabled
- core Spring rules enabled
- generic placeholder scanning enabled
- default YAML consumer enabled
- optional ecosystem packs can be enabled broadly at first

Later optimization:

- suggest disabling unused detector packs
- suggest enabling missing detector packs
- suggest adding custom rules for repeated uncertain patterns
- suggest deleting stale custom rules that no longer match anything

Example tuning report:

```yaml
ruleTuning:
  disableCandidates:
    - rulePack: redisson
      reason: no Redisson dependency, config file, or matching finding
    - rulePack: quartz
      reason: no quartz dependency or quartz.properties found
  enableCandidates:
    - rulePack: kafka
      reason: spring-kafka dependency and @KafkaListener found
  addRuleCandidates:
    - type: methodCall
      reason: repeated unknown wrapper ConfigCenter.getString
      owner: com.acme.ConfigCenter
      method: getString
      keyArg: 0
  staleRules:
    - id: legacy-config-get
      reason: no matches in last scan
```

Skill role:

- explain tuning suggestions in plain language
- help users decide whether to disable a rule pack
- turn repeated uncertain findings into custom rules
- keep `config-radar-rules.yaml` small and relevant

This keeps the first scan broad, while letting mature projects become faster and cleaner over time.
