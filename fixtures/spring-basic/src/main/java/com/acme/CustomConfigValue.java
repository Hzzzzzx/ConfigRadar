package com.acme;

public @interface CustomConfigValue {
    String key();

    String configuredValue() default "";

    String defaultValue() default "";
}
