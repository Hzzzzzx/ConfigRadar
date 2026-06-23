package io.github.hzzzzzx.configradar.core.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One secret entry in the J2C (Java-to-Connection) template format.
 *
 * <p>Sensitive configuration keys (passwords, secrets, tokens) are exported into this section
 * instead of the plain {@code app_configs} list. The {@code password} value is a placeholder
 * derived from the key (underscore form), because the real secret is provisioned out-of-band
 * via an encryption interface at the downstream config center.
 *
 * <p>Connection metadata ({@code init_source}, {@code type}, {@code account}, {@code scope}) is
 * given sensible defaults or left empty, since ConfigRadar cannot discover most of it statically.
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
