package com.acme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;

@ConfigurationProperties(prefix = "client")
@PropertySource("classpath:extra-client.properties")
@PropertySources({
    @PropertySource("classpath:redis.properties"),
    @PropertySource("classpath:kafka.properties")
})
public class DemoConfig {
    @Value("${payment.timeout:30}")
    private int timeout;

    @Value("jdbc://${db.host:localhost}:${db.port:5432}/main")
    private String jdbcUrl;

    @CustomConfigValue(key = "custom.annotated", defaultValue = "yes")
    private String annotated;

    @CustomConfigValue(key = "custom.placeholder", defaultValue = "${custom.placeholder.default:no}")
    private String annotatedPlaceholder;

    public String read(Environment environment, String prefix) {
        var direct = environment.getProperty("feature.enabled");
        var required = environment.getRequiredProperty("db.url");
        var typed = environment.getProperty("http.timeout", Integer.class, 2500);
        var hasCache = environment.containsProperty("cache.enabled");
        var binder = org.springframework.boot.context.properties.bind.Binder.get(environment)
            .bind("client.pool", String.class);
        var system = System.getProperty("app.mode", "local");
        var env = System.getenv("APP_SECRET");
        var legacyPort = Integer.getInteger("legacy.port", 8081);
        var legacyLimit = Long.getLong("legacy.limit", 10L);
        var legacyEnabled = Boolean.getBoolean("legacy.enabled");
        var dynamic = environment.getProperty(prefix + ".url");
        var custom = ConfigCenter.get("custom.center", "fallback");
        System.setProperty("runtime.region", "cn");
        return direct + required + typed + hasCache + binder + system + env + legacyPort + legacyLimit + legacyEnabled + dynamic + custom;
    }

    public void springApplicationDefaults() {
        var app = new org.springframework.boot.SpringApplication(DemoConfig.class);
        app.setDefaultProperties(java.util.Map.of(
            "spring.main.banner-mode", "off",
            "management.endpoints.web.exposure.include", "health,info"
        ));
        new org.springframework.boot.builder.SpringApplicationBuilder(DemoConfig.class)
            .properties("spring.lifecycle.timeout-per-shutdown-phase=20s");
    }

    @Profile({"prod", "staging"})
    @ConditionalOnProperty(prefix = "feature", name = {"enabled", "beta"}, havingValue = "true")
    public Object conditionalBean() {
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
