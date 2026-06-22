# ConfigRadar

ConfigRadar is an extensible configuration inventory and change analysis tool for Java/Spring applications.

The goal is to help teams understand configuration assets before deployment or review:

- what configuration keys exist
- where they are defined
- where they are read
- which environments or profiles they belong to
- what changed between two versions
- which findings are uncertain and need human review

ConfigRadar should start small: collect common Java/Spring configuration facts, output a default YAML inventory, and leave clean extension points for project-specific detectors and downstream consumers.

Start with [docs/plan.md](docs/plan.md) for the organized plan.

The longer [docs/design-draft.md](docs/design-draft.md) keeps raw discussion notes and detailed candidates.

## Development

Requirements:

- JDK 21+
- Maven 3.9+

Run tests:

```bash
mvn test
```

Current implementation status:

- Maven modules: `config-radar-core`, `config-radar-cli`
- Core skeleton: typed models, hooks, scan pipeline, YAML output, metrics sidecar
- CLI: `inventory` and key-based `diff`
- Implemented detectors:
  - Spring `application*.yml/yaml/properties` definitions and placeholder dependencies
  - Simple Typesafe Config HOCON `application.conf` / `reference.conf` key-value definitions
  - Spring Boot `spring-configuration-metadata.json` and `additional-spring-configuration-metadata.json`
  - Runtime XML placeholders, `web.xml` params, plus Logback and Log4j2 `springProperty`
  - Java source annotation placeholders, common `@Value` SpEL property references, class/method `@ConfigurationProperties`, `@ConditionalOnProperty`, `@ConditionalOnExpression`, `@Profile`, profile predicate calls, `@PropertySource`, `SpringApplication` default/command-line properties and additional profiles, programmatic Spring `PropertySource` entries, dynamic `PropertiesPropertySource` entries, Apollo `ConfigService` reads, Nacos `ConfigService.getConfig(...)` and `@NacosPropertySource` source entries, Servlet `getInitParameter`, JNDI `java:comp/env` lookups, generic config getters such as Typesafe Config / Apache Commons Configuration / MicroProfile Config, Java `Preferences` and `ResourceBundle` getters, `Environment.getProperty`, `Environment.getRequiredProperty`, `System.getProperty`, `System.getProperties`, `System.getenv`, `System.console` input, `Integer.getInteger`, `Long.getLong`, `Boolean.getBoolean`
- Spring YAML multi-document profile detection via `spring.config.activate.on-profile`
- Spring profile/config control keys, including env-style keys such as `SPRING_PROFILES_ACTIVE`, plus `@Profile` and `@PropertySource` findings are marked as `METADATA`
- Project rules: Java method-call and annotation rules from `config-radar-rules.yaml`
- Project rules: custom YAML/properties config file patterns from `config-radar-rules.yaml`
- CLI auto-loads `<projectRoot>/config-radar-rules.yaml` when `--rules` is omitted
- Basic key normalization for case, underscore, hyphen, and camelCase variants
- Dynamic/uncertain keys produce high-risk inventory checks
- Remote config center references produce review checks without fetching remote values
- Sensitive-looking key names produce review checks
- Optional sensitive value redaction with `--redact-sensitive`
- OpenRewrite and deeper symbol tracking are intentionally not implemented yet

## Project Rules

Put `config-radar-rules.yaml` in the project root, or pass it with `--rules`.

```yaml
methodCalls:
  - id: acme-config-get
    owner: ConfigCenter
    method: get
    keyArg: 0
    defaultArg: 1
    confidence: HIGH
    role: READ
  - id: acme-config-set
    owner: ConfigCenter
    method: set
    keyArg: 0
    valueArg: 1
    confidence: HIGH
    role: DEFINE

annotations:
  - id: acme-value
    type: CustomConfigValue
    keyAttribute: key
    valueAttribute: configuredValue
    defaultAttribute: defaultValue
    confidence: MEDIUM
    role: READ

configFiles:
  - id: deploy-props
    pattern: deploy/*.properties
    format: PROPERTIES
    scope: RUNTIME
```

`keyArg`, `defaultArg`, and `valueArg` are zero-based method argument indexes. `keyAttribute`, `valueAttribute`, and `defaultAttribute` are annotation attribute names. `role` can be `DEFINE`, `READ`, `CONDITION`, or `METADATA`; it defaults to `READ` for Java rules.
