# Migrating from 0.x to 1.0.0

> **Type**: Reference · [← Documentation hub](README.en.md) · [简体中文](MIGRATION_0.x_to_1.0.md)

## Version mapping

| 0.x | 1.0.0 |
|-----|--------|
| Maven `0.1.0` / `0.2.0` | `1.0.0` |
| Admin default port 8080 | Unchanged; use `application-local.yml` for local overrides |
| Log live stream | **SSE + WebSocket** dual channel (WS enabled by default) |

## Dependency upgrade

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Configuration changes

### New / changed defaults

```yaml
zestflow:
  admin:
    log-live-stream:
      # 1.0 default true; disable in prod if needed
      websocket-enabled: true
```

### Node fallback config (optional)

Under node `config.fallback`:

| key | value | behavior |
|-----|-------|----------|
| `mode` | `default` | Log and return null (same as 0.x) |
| `mode` | `constant` | Requires `constant`; writes into chain context |
| `mode` | `propagate` | Re-throws the original exception (fallback fails) |

`fallback.component` (fallback component id) still takes precedence when set.

Example:

```json
"config": {
  "fallback": {
    "mode": "constant",
    "constant": "{\"ok\":true}"
  }
}
```

## Database

- **Admin DB**: keep using Flyway `db/migration/V*.sql`; upgrade from 0.x by **restarting Admin** (no drop required).
- **Executor/Collector business DBs**: still `init.sql` + incremental scripts; no breaking 1.0 DDL.

## API stability (frozen at 1.0)

The following remain backward compatible within **1.x**; breaking changes only in **2.0**:

- Netty `POST /execute` response body (`ChainExecuteResultDTO`)
- `POST /registry/register`, `/registry/heartbeat`
- Collector `chain_event` event fields
- Admin JWT login and `Authorization: Bearer`

See [ARCHITECTURE.en.md](ARCHITECTURE.en.md) §8.5 for the full matrix.

## Verification after upgrade

```powershell
# Full StrictV1 (recommended before production cutover)
.\scripts\blackbox\run-v1-acceptance.ps1

# Quick runtime check (skip whitebox)
.\scripts\blackbox\run-v1-acceptance.ps1 -SkipMavenTest
```

See [guides/STRICT_V1_ACCEPTANCE.md](guides/STRICT_V1_ACCEPTANCE.md) for the complete 8080 stack checklist.
