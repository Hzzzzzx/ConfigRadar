package com.acme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.scheduling.annotation.Scheduled;

@ConfigurationProperties(prefix = "client")
@PropertySource("classpath:extra-client.properties")
@PropertySources({
    @PropertySource("classpath:redis.properties"),
    @PropertySource("classpath:kafka.properties")
})
public class DemoConfig {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(DemoConfig.class, args);
    }

    @Value("${payment.timeout:30}")
    private int timeout;

    @Value("${java.shell.default:-fallback}")
    private String javaShellDefault;

    @Value("${empty.default:}")
    private String emptyDefault;

    @Value("jdbc://${db.host:localhost}:${db.port:5432}/main")
    private String jdbcUrl;

    @Value("#{environment['spel.timeout']}")
    private String spelTimeout;

    @Value("#{systemEnvironment['SPEL_SECRET']}")
    private String spelSecret;

    @Value("#{systemProperties['spel.mode']}")
    private String spelMode;

    @Value("#{environment.getProperty('spel.method.timeout')}")
    private String spelMethodTimeout;

    @Value("#{systemEnvironment.get('SPEL_METHOD_SECRET')}")
    private String spelMethodSecret;

    @Value("#{systemProperties.getProperty('spel.method.mode')}")
    private String spelMethodMode;

    @CustomConfigValue(key = "custom.annotated", configuredValue = "live", defaultValue = "yes")
    private String annotated;

    @CustomConfigValue(key = "custom.placeholder", defaultValue = "${custom.placeholder.default:no}")
    private String annotatedPlaceholder;

    public String read(Environment environment, String prefix) {
        var direct = environment.getProperty("feature.enabled");
        var required = environment.getRequiredProperty("db.url");
        var typed = environment.getProperty("http.timeout", Integer.class, 2500);
        var hasCache = environment.containsProperty("cache.enabled");
        var resolved = environment.resolvePlaceholders("${resolved.placeholder:ok}");
        var prodProfile = environment.acceptsProfiles("prod");
        var regionProfile = environment.matchesProfiles("region-cn");
        var binder = org.springframework.boot.context.properties.bind.Binder.get(environment)
            .bind("client.pool", String.class);
        var createdBinder = org.springframework.boot.context.properties.bind.Binder.get(environment)
            .bindOrCreate("client.cache", String.class);
        var system = System.getProperty("app.mode", "local");
        var env = System.getenv("APP_SECRET");
        var mapEnv = System.getenv().get("MAP_SECRET");
        var mapEnvDefault = System.getenv().getOrDefault("MAP_REGION", "cn");
        var hasEnvFlag = System.getenv().containsKey("ENV_FEATURE_FLAG");
        var propertyMapValue = System.getProperties().getProperty("map.property.mode", "safe");
        var hasPropertyMapFlag = System.getProperties().containsKey("map.property.flag");
        System.getProperties().put("map.property.write", "enabled");
        System.getProperties().setProperty("map.property.set", "ready");
        var legacyPort = Integer.getInteger("legacy.port", 8081);
        var legacyLimit = Long.getLong("legacy.limit", 10L);
        var legacyEnabled = Boolean.getBoolean("legacy.enabled");
        var jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        var dynamic = environment.getProperty(prefix + ".url");
        var operatorMode = System.console().readLine("operator.mode");
        var custom = ConfigCenter.get("custom.center", "fallback");
        var customDefined = ConfigCenter.set("custom.defined", "enabled");
        System.setProperty("runtime.region", "cn");
        return direct + required + typed + hasCache + resolved + prodProfile + regionProfile + binder + createdBinder + system
            + env + mapEnv + mapEnvDefault + hasEnvFlag
            + propertyMapValue + hasPropertyMapFlag
            + legacyPort + legacyLimit + legacyEnabled + jvmArgs + dynamic + operatorMode + custom + customDefined;
    }

    public String readResolver(PropertyResolver resolver) {
        var endpoint = resolver.getProperty("resolver.endpoint", "http://localhost");
        var required = resolver.getRequiredProperty("resolver.required");
        var resolved = resolver.resolveRequiredPlaceholders("${resolver.placeholder.required}");
        return endpoint + required + resolved;
    }

    public void springApplicationDefaults() {
        var app = new org.springframework.boot.SpringApplication(DemoConfig.class);
        var defaultProperties = new java.util.Properties();
        app.setDefaultProperties(java.util.Map.of(
            "spring.main.banner-mode", "off",
            "management.endpoints.web.exposure.include", "health,info"
        ));
        app.setDefaultProperties(defaultProperties);
        new org.springframework.boot.builder.SpringApplicationBuilder(DemoConfig.class)
            .properties("spring.lifecycle.timeout-per-shutdown-phase=20s")
            .properties(new String[] {
                "spring.array.one=1",
                "spring.array.two=2"
            });
    }

    public void springApplicationCommandLineArgs() {
        org.springframework.boot.SpringApplication.run(
            DemoConfig.class,
            "--cli.mode=on",
            "--cli.timeout=30"
        );
    }

    public void programmaticPropertySource(org.springframework.core.env.ConfigurableEnvironment environment) {
        var properties = new java.util.Properties();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
            "programmatic",
            java.util.Map.ofEntries(
                java.util.Map.entry("programmatic.endpoint", "https://local"),
                java.util.Map.entry("programmatic.timeout", "PT5S")
            )
        ));
        environment.getPropertySources().addLast(
            new org.springframework.core.io.support.ResourcePropertySource("classpath:programmatic.properties")
        );
        environment.getPropertySources().addLast(
            new org.springframework.core.env.PropertiesPropertySource("programmatic-properties", properties)
        );
    }

    @Profile({"prod", "staging"})
    @ConditionalOnProperty(prefix = "feature", name = {"enabled", "beta"}, havingValue = "true")
    public Object conditionalBean() {
        return new Object();
    }

    @ConditionalOnExpression("'${feature.expression.enabled:false}' == 'true' && environment['expression.mode'] == 'blue'")
    public Object expressionConditionalBean() {
        return new Object();
    }

    @Bean
    @ConfigurationProperties(prefix = "redisson.client")
    public Object redissonClient() {
        return new Object();
    }

    @Scheduled(cron = "${jobs.cleanup.cron:0 0 * * * *}", fixedDelayString = "${jobs.cleanup.delay:60000}")
    public void cleanup() {
    }
}
