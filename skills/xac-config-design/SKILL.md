---
name: xac-config-design
description: Hand-author XAC-format deployment config YAML (app_configs + J2C.secrets) for a Java/Spring application during the onboarding/integration phase, before code is implemented. Use when the design calls for writing the XAC config file by hand into a project directory (e.g. a top-level XAC/ directory) rather than generating it from a scan, when you need to decide for each config key whether it is normal config or a J2C secret, and when a secret key must carry only a placeholder value. Triggers on phrases like "write the XAC config", "XAC YAML by hand", "接入手写 XAC 配置", "区分普通配置和 J2C", "XAC 配置骨架", "fill in XAC config keys and values", "敏感配置占位符".
---

# XAC Config Design (hand-authored)

Write the XAC deployment-platform config YAML **by hand** during the integration/onboarding phase, so the application's config contract exists before (or alongside) the code. This is the manual counterpart to ConfigRadar's machine-generated XAC export: you supply the keys and values directly from the design.

## When to use

The Agent is about to implement code and must first produce the XAC config artifact in a fixed project location. Concretely:

- The design step requires an XAC-format YAML written into a directory (default: a top-level `XAC/` directory at the project root, or whatever path the design/spec names).
- Each config key must be classified as **normal config** (`data.app_configs`) or **J2C secret** (`data.J2C.secrets`).
- J2C secrets keep the **key** but use a **placeholder value** — the real secret is provisioned out-of-band via an encryption interface, never written into the file.

This skill does NOT scan code or run ConfigRadar. For scan-driven XAC output use the `config-radar` skill instead.

## Output location & file naming

Write exactly one file, named after the application:

```
<project-root>/XAC/<application-name>.yaml
```

- Default directory: `XAC/` at the project root. Use a different path only if the design explicitly names one.
- One application = one file. Do not split `app_configs` and `J2C` into separate files; they live together under one `data:` block.

## File structure (always this shape)

```yaml
apiVersion: com.huawei.his.appconfigcenter.v3   # fixed — copy verbatim
kind: his.appconfigcenter                        # fixed — copy verbatim
metadata:
  name: <application-name>                        # the application's deploy name
data:
  app_configs:                                    # normal (non-sensitive) config — has real values
    - <AppConfigEntry>                            # see "Normal config entry" below
  J2C:                                            # sensitive config — key only, placeholder value
    secrets:
      - <J2cSecretEntry>                          # see "J2C secret entry" below
    scope: <scope>
```

Keep `apiVersion`, `kind` verbatim. `metadata.name` is the application's deploy name (often the Maven `artifactId` or the service name).

## The one classification rule

For every key, decide **normal config** vs **J2C secret** by the key name and meaning:

| Class | When | Where | Value |
|---|---|---|---|
| **Normal config** | Non-sensitive runtime config: port, timeout, pool size, flag, path, URL, driver class. Key name does **not** hint at a secret. | `data.app_configs` | the **real value** from the design |
| **J2C secret** | Key name or meaning looks sensitive: `password`, `passwd`, `secret`, `token`, `credential`, `apiKey`, `accessKey`, private key material, anything you would not commit in plaintext. | `data.J2C.secrets` | **placeholder only** — `${underscore_form_of_key}` |

**When unsure, treat the key as a J2C secret.** Routing a real value to `app_configs` exposes it in the repo; a false-positive in J2C only costs a placeholder that the real value later overrides.

### Classifying from a Spring key

- `spring.datasource.password`, `db.password`, `redis.password` → J2C secret.
- `spring.datasource.url`, `spring.datasource.driver-class-name`, `spring.datasource.hikari.maximum-pool-size` → normal config.
- `spring.redis.host` → normal config; `spring.redis.password` → J2C secret.
- `management.endpoint.health.secret` (name contains "secret") → J2C secret unless the design states it is not actually sensitive.
- `xxx.token`, `xxx.access-key`, `xxx.api-key`, `xxx.credential` → J2C secret.

## Normal config entry (`app_configs` item)

```yaml
- scope: <scope>                       # deploy unit; "${app_deploy_unit_name}" when unknown at design time
  group_name: <group>                  # first segment of the config key before a dot; "default" if none
  config_key: <normalized-key>         # the key in normalized form, e.g. server.port
  config_value: <real-value>           # the value from the design, as a string
  secret: 0                            # always 0 here; sensitive keys go to J2C, never here
  sub_application_id: ""               # leave empty unless the design specifies
  version: "1.0"                       # default "1.0"; set from the design when known
  docker_version: "1.0"                # default "1.0"; set from the design when known
  remark: ""                           # short note, or empty
```

- `group_name` = the substring before the first `.` of `config_key` (`server.port` → `server`; a key with no dot → `default`).
- `config_value` is always emitted as a string (`8080` → `"8080"`, `true` → `"true"`).
- If the value is unknown at design time, leave `config_value: ""` and add a `remark` noting it is pending.

## J2C secret entry (`J2C.secrets` item)

**The key is real; the value is a placeholder.** Never put the actual secret in the file.

```yaml
- key: <underscore_key>                # config key with . and - replaced by _, lowercased: db.password -> db_password
  init_source: input                   # "input" = entered manually at the config center; default
  type: <type-hint>                    # best-effort backend hint, or empty; see below
  account: ""                          # leave empty unless the design gives an account/login
  password: ${<underscore_key>}        # PLACEHOLDER only — the real secret is provisioned out-of-band
  encrypt_type: ADVANCED2.6            # fixed encryption type; copy verbatim
  remark: <short-note-or-empty>        # default to the type hint, or empty
  scope: <scope>                       # deploy unit; same resolution as app_configs
```

- `key` and the placeholder inside `password` are the **same** underscore form. For `db.password` → `key: db_password`, `password: ${db_password}`.
- `password` is ALWAYS a placeholder. The real value comes from the encryption interface at deploy time.
- `type` hint (best-effort from the key, empty when unclear): `redis` for redis-related keys; `mysql` for DB/datasource/jdbc keys; otherwise omit/empty.
- The `J2C` block also has a top-level `scope:` field (sibling of `secrets:`); set it the same way as entry scopes.

## `scope` resolution

`scope` is deploy-time metadata the design usually cannot fully know. Set it by this priority:

1. A value the design explicitly states for this key/application (e.g. `obp-prod`).
2. A profile→scope mapping the design supplies (one scope per Spring profile).
3. The placeholder `${app_deploy_unit_name}` when nothing is known — the deploy pipeline resolves it.

Default to `${app_deploy_unit_name}` rather than inventing a scope. State in your summary that scopes are placeholders pending deploy-time resolution.

## How to author — step by step

1. **List every config key** the application needs, from the design/spec/PRD. Do not infer keys by scanning code in this skill.
2. **For each key, classify**: normal config or J2C secret (rule above). When unsure, J2C secret.
3. **Fill the value**:
   - Normal config → the real value from the design. If the design does not give one, leave `config_value: ""` with a `remark` and flag it for the human.
   - J2C secret → placeholder `${underscore_key}` only. Never the real secret.
4. **Derive `group_name`** and `scope` mechanically per the rules above.
5. **Write the single file** to `<project-root>/XAC/<application-name>.yaml`, sorted: `app_configs` alphabetically by `config_key`, then `J2C.secrets` alphabetically by `key`.
6. **Report back**: how many normal config keys, how many J2C secrets, which values are pending/placeholder, and that `scope`/`version` are deploy-time placeholders.

## Reference detail

For the full field-by-field schema, worked normal + J2C examples, the secret `type`-hint table, and an end-to-end filled sample, see [references/xac-schema.md](references/xac-schema.md). Read it when you need exact field semantics or a complete worked example to copy from.

A ready-to-edit skeleton lives at [assets/xac-config.template.yaml](assets/xac-config.template.yaml). Copy it into `XAC/<application-name>.yaml` and fill it in.

## What NOT to do

- Do **not** write a real secret value into `password`. Placeholders only.
- Do **not** put sensitive keys in `app_configs` (their `secret` must be `0`); route them to `J2C.secrets`.
- Do **not** invent a `config_value` when the design does not supply one — leave it empty and flag it.
- Do **not** change `apiVersion`, `kind`, or `encrypt_type` — they are fixed.
- Do **not** scan code or run ConfigRadar here. This skill is hand-authoring only.
