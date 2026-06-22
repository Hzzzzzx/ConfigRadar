package io.github.hzzzzzx.configradar.core.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/** Shared YAML mapper for inventories, diffs, and rules. */
public final class YamlSupport {
    private YamlSupport() {
    }

    /**
     * Creates a YAML mapper with ConfigRadar's public serialization defaults.
     *
     * <p>The mapper accepts unknown fields to keep old binaries tolerant of newer YAML schemas.
     * Callers receive a new mapper instance so tests and future consumers can tune their own
     * ObjectMapper without mutating global state.</p>
     *
     * @return YAML mapper for inventory, diff, run report, and rules files
     */
    public static ObjectMapper mapper() {
        return YAMLMapper.builder(new YAMLFactory())
            .addModule(new Jdk8Module())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();
    }
}
