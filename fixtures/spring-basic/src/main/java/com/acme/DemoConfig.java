package com.acme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;

@ConfigurationProperties(prefix = "client")
@PropertySource("classpath:extra-client.properties")
@PropertySource(name = "named-extra", value = "classpath:named-extra.properties")
@PropertySources({
    @PropertySource("classpath:redis.properties"),
    @PropertySource("classpath:kafka.properties")
})
@com.alibaba.nacos.api.config.annotation.NacosPropertySource(dataId = "shared.yaml", groupId = "DEFAULT_GROUP")
public class DemoConfig {
    @FeignClient(name = "${inventory.client.name:inventory}", url = "${inventory.client.url:http://localhost}")
    interface InventoryClient {
    }

    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(DemoConfig.class, args);
        new org.springframework.boot.builder.SpringApplicationBuilder(DemoConfig.class).run(args);
    }

    @Value("${payment.timeout:30}")
    private int timeout;

    @Value("${java.shell.default:-fallback}")
    private String javaShellDefault;

    @Value("${empty.default:}")
    private String emptyDefault;

    @Value("${nested.outer:${nested.inner:42}}")
    private String nestedDefault;

    @Value("${nested.shell.outer:${nested.shell.inner:-fallback}}")
    private String nestedShellDefault;

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

    @Value("#{systemProperties.get('spel.method.raw')}")
    private String spelMethodRaw;

    @com.alibaba.nacos.api.config.annotation.NacosValue(value = "${nacos.feature.enabled:true}", autoRefreshed = true)
    private String nacosFeatureEnabled;

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
        var arrayProfile = environment.acceptsProfiles(new String[] {"qa", "perf"});
        var cloudProfile = environment.matchesProfiles(new String[] {"cloud"});
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
        var propertyMapRawValue = System.getProperties().get("map.property.raw");
        var propertyMapDefaultValue = System.getProperties().getOrDefault("map.property.default", "safe-default");
        var hasPropertyMapFlag = System.getProperties().containsKey("map.property.flag");
        System.getProperties().put("SPRING_APPLICATION_JSON", "{\"javaEnv\":{\"json\":{\"limit\":5}}}");
        System.getProperties().put("map.property.write", "enabled");
        System.getProperties().putIfAbsent("map.property.default-write", "lazy");
        System.getProperties().replace("map.property.replaced", "old", "new");
        System.getProperties().replace("map.property.replaced-direct", "direct");
        System.getProperties().setProperty("map.property.set", "ready");
        System.getProperties().remove("map.property.removed");
        var legacyPort = Integer.getInteger("legacy.port", 8081);
        var legacyLimit = Long.getLong("legacy.limit", 10L);
        var legacyEnabled = Boolean.getBoolean("legacy.enabled");
        var jvmArgs = java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments();
        var replacementProperties = new java.util.Properties();
        System.setProperties(replacementProperties);
        var dynamic = environment.getProperty(prefix + ".url");
        var operatorMode = System.console().readLine("operator.mode");
        var custom = ConfigCenter.get("custom.center", "fallback");
        var customDefined = ConfigCenter.set("custom.defined", "enabled");
        var apolloApp = com.ctrip.framework.apollo.ConfigService.getAppConfig()
            .getProperty("apollo.app.timeout", "3000");
        var apolloNamespace = com.ctrip.framework.apollo.ConfigService.getConfig("orders")
            .getProperty("apollo.orders.enabled", "true");
        var nacosConfig = com.alibaba.nacos.api.config.ConfigService.getConfig(
            "orders.yaml",
            "DEFAULT_GROUP",
            3000
        );
        System.setProperty("runtime.region", "cn");
        System.setProperty("spring.application.json", "{\"java\":{\"json\":{\"enabled\":true}}}");
        System.clearProperty("runtime.mode");
        return direct + required + typed + hasCache + resolved + prodProfile + regionProfile + arrayProfile + cloudProfile
            + binder + createdBinder + system
            + env + mapEnv + mapEnvDefault + hasEnvFlag
            + propertyMapValue + propertyMapRawValue + propertyMapDefaultValue + hasPropertyMapFlag
            + legacyPort + legacyLimit + legacyEnabled + jvmArgs + dynamic + operatorMode + custom + customDefined
            + apolloApp + apolloNamespace + nacosConfig;
    }

    public String readResolver(PropertyResolver resolver) {
        var endpoint = resolver.getProperty("resolver.endpoint", "http://localhost");
        var required = resolver.getRequiredProperty("resolver.required");
        var resolved = resolver.resolveRequiredPlaceholders("${resolver.placeholder.required}");
        return endpoint + required + resolved;
    }

    public String readServlet(jakarta.servlet.ServletContext servletContext, String prefix) {
        var literal = servletContext.getInitParameter("servlet.feature.enabled");
        var dynamic = servletContext.getInitParameter(prefix + ".servlet");
        return literal + dynamic;
    }

    public Object readJndi(javax.naming.InitialContext context, String name) throws Exception {
        var datasource = context.lookup("java:comp/env/jdbc/orders");
        var dynamic = context.lookup("java:comp/env/" + name);
        return datasource + ":" + dynamic;
    }

    public String readGenericConfig(com.typesafe.config.Config appConfig, org.apache.commons.configuration2.Configuration configuration, String prefix) {
        var mode = appConfig.getString("typesafe.app.mode");
        var enabled = configuration.getBoolean("commons.feature.enabled", true);
        var mpMode = org.eclipse.microprofile.config.ConfigProvider.getConfig().getValue("mp.mode", String.class);
        var mpTimeout = appConfig.getOptionalValue("mp.timeout", Integer.class);
        var dynamic = appConfig.getString(prefix + ".typesafe");
        return mode + enabled + mpMode + mpTimeout + dynamic;
    }

    public String readPreferences(String prefix) {
        var preferences = java.util.prefs.Preferences.userNodeForPackage(DemoConfig.class);
        var mode = preferences.get("preferences.mode", "local");
        var limit = preferences.getInt("preferences.limit", 10);
        var dynamic = preferences.get(prefix + ".preference", "missing");
        return mode + limit + dynamic;
    }

    public Object readResourceBundle(String prefix) {
        var bundle = java.util.ResourceBundle.getBundle("application");
        var title = bundle.getString("bundle.title");
        var logo = bundle.getObject("bundle.logo");
        var dynamic = bundle.getString(prefix + ".bundle");
        return title + ":" + logo + ":" + dynamic;
    }

    public void springApplicationDefaults() {
        var app = new org.springframework.boot.SpringApplication(DemoConfig.class);
        var defaultProperties = new java.util.Properties();
        app.setAdditionalProfiles("blue", "canary");
        app.setAdditionalProfiles(new String[] {"green", "gold"});
        app.setDefaultProperties(java.util.Map.of(
            "spring.main.banner-mode", "off",
            "management.endpoints.web.exposure.include", "health,info"
        ));
        app.setDefaultProperties(defaultProperties);
        new org.springframework.boot.builder.SpringApplicationBuilder(DemoConfig.class)
            .profiles("builder-prod")
            .profiles(new String[] {"builder-blue", "builder-green"})
            .properties(java.util.Map.of("spring.builder.map", "on"))
            .properties(java.util.Map.ofEntries(java.util.Map.entry("spring.builder.entry", "yes")))
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
        org.springframework.boot.SpringApplication.run(
            DemoConfig.class,
            new String[] {
                "--cli.array.mode=on",
                "--cli.array.timeout=60"
            }
        );
        new org.springframework.boot.builder.SpringApplicationBuilder(DemoConfig.class).run(
            "--builder.cli.mode=on",
            "--builder.cli.timeout=45"
        );
        new org.springframework.boot.builder.SpringApplicationBuilder(DemoConfig.class).run(new String[] {
            "--builder.cli.array.mode=on",
            "--builder.cli.array.timeout=75"
        });
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
            new org.springframework.core.io.support.ResourcePropertySource(
                "named-programmatic",
                "classpath:named-programmatic.properties"
            )
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
    @SchedulerLock(name = "cleanup", lockAtMostFor = "${jobs.cleanup.lock-at-most:PT5M}")
    public void cleanup() {
    }

    @KafkaListener(topics = "${kafka.orders.topic:orders}", groupId = "${kafka.orders.group:config-radar}")
    public void consumeKafka(String message) {
    }

    @RabbitListener(queues = "${rabbit.orders.queue:orders.queue}")
    public void consumeRabbit(String message) {
    }

    @JmsListener(destination = "${jms.orders.destination:orders.destination}")
    public void consumeJms(String message) {
    }

    @CircuitBreaker(name = "${resilience.orders.circuitbreaker:orders}")
    @Retry(name = "${resilience.orders.retry:orders}")
    @RateLimiter(name = "${resilience.orders.ratelimiter:orders}")
    @Bulkhead(name = "${resilience.orders.bulkhead:orders}")
    public String callInventory() {
        return "ok";
    }

    @Cacheable(cacheNames = "${cache.orders.name:orders}", key = "${cache.orders.key:default}")
    public String cachedOrder(String id) {
        return id;
    }

    @Async("${async.orders.executor:ordersExecutor}")
    public void asyncOrder() {
    }
}
