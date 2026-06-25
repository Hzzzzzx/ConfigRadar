package io.github.hzzzzzx.configradar.xac;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One secret entry in the J2C (Java-to-Connection) template format for the XAC platform.
 *
 * <p>Sensitive configuration keys (passwords, secrets, tokens) are routed here instead of the
 * plain {@code app_configs} list. The {@code password} value is a placeholder derived from the
 * key (underscore form), because the real secret is provisioned out-of-band via an encryption
 * interface at the downstream config center.
 */
@JsonPropertyOrder({
    "key",
    "init_source",
    "type",
    "account",
    "password",
    "encrypt_type",
    "remark",
    "scope"
})
@JsonInclude(JsonInclude.Include.ALWAYS)
public record J2cSecretEntry(
    String key,
    String init_source,
    String type,
    String account,
    String password,
    String encrypt_type,
    String remark,
    String scope
) {
}
