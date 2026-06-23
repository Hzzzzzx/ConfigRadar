package io.github.hzzzzzx.configradar.core.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One entry in the application-config-center format produced by {@link AppConfigCenterExporter}.
 *
 * <p>This mirrors the downstream {@code app_configs} schema: a flat list of config key/value
 * records that teams load into their internal config center. Deploy-time metadata
 * ({@code scope}, {@code sub_application_id}, {@code version}, {@code docker_version},
 * {@code remark}) is left empty because ConfigRadar cannot discover it statically; consumers
 * fill it in later.
 */
@JsonPropertyOrder({
    "scope",
    "group_name",
    "config_key",
    "config_value",
    "secret",
    "sub_application_id",
    "version",
    "docker_version",
    "remark"
})
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AppConfigEntry(
    String scope,
    String group_name,
    String config_key,
    Object config_value,
    int secret,
    String sub_application_id,
    String version,
    String docker_version,
    String remark
) {
}
