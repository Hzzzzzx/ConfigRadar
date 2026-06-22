# Coverage

## Strategy

Start with broad and reliable core coverage:

```text
Java Core + Spring Core + generic placeholders + common config files
```

Do not hand-build every third-party component in Phase 1. Support them later through detector/rule packs.

## Phase 1 Must

Spring config files:

- `application*.yml`
- `application*.yaml`
- `application*.properties`
- `bootstrap*.yml`
- `bootstrap*.yaml`
- `bootstrap*.properties`

Java/Spring direct reads:

- `System.getProperty`
- `System.getenv`
- `Environment.getProperty`
- `Environment.getRequiredProperty`
- `PropertyResolver.getProperty`
- `PropertyResolver.getRequiredProperty`

Spring annotations:

- `@Value`
- `@ConfigurationProperties`
- `@ConditionalOnProperty`
- `@PropertySource`
- `@Profile`

Generic placeholders:

- `${key}`
- `${key:default}`
- annotation attributes
- YAML/properties/XML files

Outputs:

- default YAML inventory
- uncertain findings
- uncertain summary
- key/full inventory diff

## Phase 1 Should

- `static final String` constants
- simple string prefix composition
- Spring Boot metadata JSON
- simple custom rule YAML for method calls, annotations, and config file patterns

## Later Coverage

- project profiling and generated rules
- CEL-guided tracing
- artifact/JAR scan
- runtime snapshot
- test-time configuration, only if a real use case appears
- remote config center value fetching
- complete Spring property precedence simulation
- many downstream consumers
- deep per-component schema modeling

## Spring Areas

Core Spring/Spring Boot coverage should include:

- `Environment`
- `PropertyResolver`
- `PropertySource`
- `@PropertySource`
- profile declarations
- `application*` and `bootstrap*` config files
- `spring.config.*`
- `spring.profiles.*`
- `@ConfigurationProperties`
- `Binder.bind`
- `@ConditionalOnProperty`
- Spring Boot metadata JSON
- test-time configuration

## Config Centers

Static scan should identify entry points and facts, not fetch remote values by default.

Targets:

- Spring Cloud Config
- Apollo
- Nacos
- Consul/Etcd
- Archaius
- custom config centers

Collect:

- imports
- namespaces
- groups
- data IDs
- direct keys
- default values
- listeners
- local fallback files
- dynamic/uncertain access patterns

## Third-Party Components

Treat third-party support as future detector/rule packs.

Candidate packs:

- Kafka/Rabbit/JMS
- Redis/Redisson/cache
- datasource/JPA/Flyway/Liquibase
- logging/observability
- Feign/Gateway
- MyBatis/Quartz
- security/OAuth2/JWT
- cloud storage/search/RPC/resilience

Use broad tactics first:

- parse common config files
- collect known Spring property prefixes
- scan annotation attributes for `${...}`
- scan XML/YAML/properties placeholders
- classify sensitive-looking keys
- mark component/source type when known
