package com.acme;

public final class ConfigCenter {
    private ConfigCenter() {
    }

    public static String get(String key, String defaultValue) {
        return defaultValue;
    }
}
