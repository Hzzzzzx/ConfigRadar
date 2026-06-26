# XAC Schema — field-by-field reference

The XAC deployment-platform config artifact. One file per application, written to `<project-root>/XAC/<application-name>.yaml`.

## Table of contents

- [Top-level shape](#top-level-shape)
- [app_configs entry (normal config)](#app_configs-entry-normal-config)
- [J2C.secrets entry (sensitive config)](#j2csecrets-entry-sensitive-config)
- [Secret `type`-hint table](#secret-type-hint-table)
- [Underscore-key derivation](#underscore-key-derivation)
- [Full worked example](#full-worked-example)
- [Decision: normal config vs J2C secret](#decision-normal-config-vs-j2c-secret)

## Top-level shape

```yaml
apiVersion: com.huawei.his.appconfigcenter.v3   # FIXED
kind: his.appconfigcenter                        # FIXED
metadata:
  name: <application-name>                        # deploy name of the app (artifactId / service name)
data:
  app_configs:                                    # list of normal (non-sensitive) config entries
    - { ...AppConfigEntry }
  J2C:                                            # sensitive config block
    secrets:                                      # list of secret entries (key + placeholder value)
      - { ...J2cSecretEntry }
    scope: <scope>                                # deploy scope for the whole J2C block
```

- `apiVersion`, `kind`, `encrypt_type`: fixed constants. Copy verbatim, never edit.
- `metadata.name`: the application name. If the design does not state it, use the Maven `artifactId` or service name.
- `app_configs` and `J2C` both live under `data:` in the **same file**.

## app_configs entry (normal config)

Each item is an `AppConfigEntry`:

| Field | Required | Value |
|---|---|---|
| `scope` | yes | deploy unit. `${app_deploy_unit_name}` when unknown at design time. |
| `group_name` | yes | first segment of `config_key` before the first `.`; `default` when no dot. |
| `config_key` | yes | the key, normalized. Keep dots/dashes as-is (e.g. `server.port`, `my.feature-flag`). |
| `config_value` | yes | the **real value** as a string. `8080`→`"8080"`, `true`→`"true"`. Empty string `""` when unknown — flag it. |
| `secret` | yes | always `0` here. Sensitive keys never appear in `app_configs`. |
| `sub_application_id` | yes | empty `""` unless the design specifies a sub-application id. |
| `version` | yes | `"1.0"` by default; set from the design when known. |
| `docker_version` | yes | `"1.0"` by default; set from the design when known. |
| `remark` | yes | short note or empty `""`. Use it to mark a pending value. |

`group_name` examples:

- `server.port` → `server`
- `spring.datasource.url` → `spring`
- `mybatis.mapper-locations` → `mybatis`
- `feature-flag-x` (no dot) → `default`
- `os.name` → `os`

## J2C.secrets entry (sensitive config)

Each item is a `J2cSecretEntry`. The **key is real; the value is a placeholder.**

| Field | Required | Value |
|---|---|---|
| `key` | yes | underscore form of the config key: replace `.` and `-` with `_`, lowercase. `db.password` → `db_password`. |
| `init_source` | yes | `input` (entered manually at the config center). Default. |
| `type` | no | best-effort backend hint: `redis`, `mysql`, or empty. See [type-hint table](#secret-type-hint-table). |
| `account` | no | account/login if the design provides one; otherwise empty `""`. |
| `password` | yes | **PLACEHOLDER** `${<key>}`. Same underscore form as `key`. NEVER the real secret. |
| `encrypt_type` | yes | `ADVANCED2.6` (fixed). |
| `remark` | no | short note; defaults to the `type` hint or empty. |
| `scope` | yes | deploy unit, same resolution as `app_configs.scope`. |

The `J2C` block carries one additional top-level field, `scope:` (a sibling of `secrets:`), for the whole block's deploy scope.

`init_source` values: `input` = manual entry at the config center (the design-time default). Other values are set by the deploy pipeline, not here.

## Secret `type`-hint table

Best-effort hint derived from the key name. Empty when unclear.

| Key contains / starts with | `type` |
|---|---|
| `redis` | `redis` |
| `db.` prefix, `datasource`, `jdbc` | `mysql` |
| anything else | _(omit / empty)_ |

Examples:

- `spring.datasource.password` → `type: mysql`, `key: spring_datasource_password`
- `spring.redis.password` → `type: redis`, `key: spring_redis_password`
- `app.jwt.secret` → no hint, `key: app_jwt_secret`
- `oss.access-key` → no hint, `key: oss_access_key`

## Underscore-key derivation

```
underscore_key(key) = key.replace('-', '_').replace('.', '_').lowercase()
```

- `db.password` → `db_password`
- `spring.datasource.password` → `spring_datasource_password`
- `app.api-key` → `app_api_key`
- empty/blank key → `config`

`key` and the `${...}` inside `password` must be identical.

## Full worked example

A Spring Boot app `order-service` with a MySQL datasource, Redis, a JWT signing key, and an OSS access key. Notice: URLs, ports, pool sizes, flags, driver class are normal config; passwords/keys/secrets are J2C with placeholders.

```yaml
apiVersion: com.huawei.his.appconfigcenter.v3
kind: his.appconfigcenter
metadata:
    name: order-service
data:
    app_configs:
        # --- server / runtime (normal config) ---
        - scope: ${app_deploy_unit_name}
          group_name: server
          config_key: server.port
          config_value: '8080'
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: ''
        - scope: ${app_deploy_unit_name}
          group_name: spring
          config_key: spring.datasource.driver-class-name
          config_value: com.mysql.cj.jdbc.Driver
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: ''
        - scope: ${app_deploy_unit_name}
          group_name: spring
          config_key: spring.datasource.url
          config_value: jdbc:mysql://db-host:3306/orders?useSSL=false
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: ''
        - scope: ${app_deploy_unit_name}
          group_name: spring
          config_key: spring.datasource.hikari.maximum-pool-size
          config_value: '10'
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: ''
        - scope: ${app_deploy_unit_name}
          group_name: spring
          config_key: spring.redis.host
          config_value: redis-host
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: ''
        - scope: ${app_deploy_unit_name}
          group_name: spring
          config_key: spring.redis.port
          config_value: '6379'
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: ''
        # --- value not yet known at design time: leave empty, flag it ---
        - scope: ${app_deploy_unit_name}
          group_name: app
          config_key: app.feature.enabled
          config_value: ''
          secret: 0
          sub_application_id: ''
          version: '1.0'
          docker_version: '1.0'
          remark: 'PENDING: confirm rollout flag default with PM'
    J2C:
        secrets:
            # sensitive keys: real key, PLACEHOLDER value only
            - key: spring_datasource_password
              init_source: input
              type: mysql
              account: ''
              password: ${spring_datasource_password}
              encrypt_type: ADVANCED2.6
              remark: mysql
              scope: ${app_deploy_unit_name}
            - key: spring_redis_password
              init_source: input
              type: redis
              account: ''
              password: ${spring_redis_password}
              encrypt_type: ADVANCED2.6
              remark: redis
              scope: ${app_deploy_unit_name}
            - key: app_jwt_secret
              init_source: input
              account: ''
              password: ${app_jwt_secret}
              encrypt_type: ADVANCED2.6
              remark: ''
              scope: ${app_deploy_unit_name}
            - key: oss_access_key
              init_source: input
              account: ''
              password: ${oss_access_key}
              encrypt_type: ADVANCED2.6
              remark: ''
              scope: ${app_deploy_unit_name}
        scope: ${app_deploy_unit_name}
```

Note how every `password:` above is a `${...}` placeholder — no real secret is committed. The `app.feature.enabled` entry shows the pending-value pattern: empty `config_value` plus a `remark`.

## Decision: normal config vs J2C secret

```
for each config key:
  is the key name or its meaning sensitive?
    (password / passwd / secret / token / credential / apiKey / accessKey / private key / ...)
    YES  -> J2C.secrets : real key, placeholder ${underscore_key}
    NO   -> app_configs : real key, REAL value from design
  unsure? -> treat as J2C secret (safer; only costs a placeholder)
```
