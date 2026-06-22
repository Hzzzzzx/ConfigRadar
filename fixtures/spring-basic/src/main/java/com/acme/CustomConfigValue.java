package com.acme;

public @interface CustomConfigValue {
    String key();

    String defaultValue() default "";
}
