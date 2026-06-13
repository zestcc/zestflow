# Configuration Reference

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](CONFIGURATION.md)  
> **Source of truth:** Each module's `application.yml` + `*Properties.java`

This document summarizes ZestFlow's main configuration prefixes. Business projects typically override only `zestflow.executor.*` and `zestflow.collector.*`; Admin deployment uses `zestflow.admin.*`.

---

## Configuration Sync Policy

When modifying `zestflow.executor.*`, sync across:

- `zestflow-executor/src/main/resources/application.yml`
- `zestflow-demo/src/main/resources/application.yml`
- `zestflow-demo/src/main/resources/application-prod.example.yml`
- `zestflow-demo/src/test/resources/application-test.yml`

When modifying `zestflow.admin.*`, sync across:

- `zestflow-admin/src/main/resources/application.yml`
- `zestflow-admin/src/main/resources/application-prod.example.yml`

---

## zestflow.executor.*

| Property | Default | Description |
|----------|---------|-------------|
| `app-code` | `spring.application.name` | Application code |
| `app-name` | Same as app-code | Display name |
| `admin-addresses` | `http://localhost:8080` | Admin addresses, comma-separated |
| `access-token` | empty | Netty `X-Access-Token`; must match Admin `executor-access-token` |
| `registry-token` | empty | Registration / heartbeat `X-Registry-Token` |
| `heartbeat-interval` | `30` | Heartbeat interval (seconds) |
| `host` | Auto-detected private IPv4 | Executor registration host |
| `port` | `20550` | Netty server port |
| `execute-endpoint-enabled` | `false` | Whether Tomcat exposes `/execute` |
| `chain-route-enabled` | `false` | Mode 2 chain HTTP routing |
| `execute-response-mode` | `BODY` | Tomcat success response: BODY / DETAIL |
| `execute-failure-policy` | `PROPAGATE` | PROPAGATE / ERROR_HANDLER / WRAPPED |
| `timeout-ms` | `5000` | Registration / heartbeat HTTP timeout |
| `tenant-id` | `1` | Tenant ID |
| `data-dir` | `./zestflow-data` | Chain / design / AI knowledge base directory |
| `idempotency-enabled` | `true` | `/execute` idempotency deduplication |
| `idempotency-ttl-ms` | `300000` | Idempotency cache TTL |
| `shard-index` / `shard-total` | `0` / `1` | Cron sharding |
| `ai-localhost-only` | `true` | When no accessToken, AI API is localhost-only |

### zestflow.executor.chain.*

| Property | Default | Description |
|----------|---------|-------------|
| `load-retry-times` | `3` | Chain load retries |
| `load-retry-interval-ms` | `5000` | Retry interval |
| `auto-reload` | `true` | Hot reload |
| `reload-check-interval-ms` | `60000` | Hot reload poll interval |
| `parallel-threshold` | `3` | Same-layer parallel limit |
| `default-timeout-ms` | `60000` | Chain default timeout |
| `node-default-retry-count` | `0` | Node default retry count |
| `declaration-sync-enabled` | `true` | `@ZestChain` placeholder sync |

### zestflow.executor.expression.*

Aviator expression engine (edge conditions / SCRIPT nodes / While loops). See [CHAIN_ORCHESTRATION.en.md](../guides/CHAIN_ORCHESTRATION.en.md) §10.

| Property | Default | Description |
|----------|---------|-------------|
| `timeout-ms` | `5000` | Per-evaluation/script timeout (ms) |
| `max-script-length` | `10000` | Max expression length (chars) |
| `max-cache-size` | `1000` | Compiled expression LRU cache size |
| `max-loop-count` | `10000` | Aviator loop iteration cap |
| `condition-fail-open` | `false` | Treat failed conditions as true (default fail-closed) |
| `clear-cache-on-chain-reload` | `true` | Clear compile cache after chain hot reload |

### zestflow.executor.event.*

Bound by `collector-jdbc`; commonly used properties:

| Property | Default | Description |
|----------|---------|-------------|
| `queue-capacity` | `8192` | Bounded queue capacity |
| `batch-size` | `200` | Batch persist size |
| `batch-max-wait-ms` | `500` | Batch wait upper bound |
| `circuit-breaker-threshold` | `10` | Consecutive failures before circuit break |
| `disk-fallback-enabled` | `false` | Disk fallback |

### zestflow.executor.schedule.*

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Executor-side scheduling switch |
| `driver` | `embedded` | `embedded` / `xxl-job` / `noop` |
| `poll-interval-ms` | `15000` | Schedule scan interval |

### zestflow.executor.ai.*

| Property | Default | Description |
|----------|---------|-------------|
| `llm-enabled` | `false` | Executor-side LLM suggest |
| `base-url` | `http://localhost:11434/v1` | OpenAI-compatible API |
| `model` | `llama3.2` | Model name |
| `rag-mode` | `hybrid` | RAG retrieval mode |
| `temperature` | `0.2` | Generation temperature |
| `repair-max-rounds` | `2` | Validate failure repair rounds |

See [adr/SCHEDULING.md](../adr/SCHEDULING.en.md) and [AI_CHAIN_LEARNING.md](../AI_CHAIN_LEARNING.en.md).

---

## zestflow.collector.*

| Property | Default | Description |
|----------|---------|-------------|
| `datasource.url` | — | Log database JDBC URL |
| `registry.port` | `9998` (module default); Demo / prod recommend `20650` | Collector Netty port |
| `registry.admin-addresses` | Same as executor | Admin registration addresses |
| `access-token` | empty | Admin query `X-Collector-Token` |
| `batch-size` | `200` | Batch write size |
| `queue-capacity` | `8192` | Async queue capacity |
| `async-enabled` | `true` | Async collection switch |

Kafka / RabbitMQ implementations: `zestflow.collector.kafka.topic`, `zestflow.collector.rabbitmq.exchange`.

---

## zestflow.admin.*

| Property | Default | Description |
|----------|---------|-------------|
| `registry-token` | empty | Machine interface token (required in production) |
| `executor-access-token` | empty | Admin → Executor Netty |
| `deploy-mode` | `standalone` | standalone / cluster |
| `protocol` | `http` | Internal communication protocol |
| `http-timeout-ms` | `5000` | HTTP client timeout |
| `cache.type` | `caffeine` | simple / caffeine / redis |

### zestflow.admin.executor-read-cache.*

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Executor GET snapshot switch |
| `ttl-minutes` | `60` | Snapshot TTL |
| `max-entries` | `500` | Max entries (Caffeine standalone only) |

When Executor is offline or unreachable, chain/design/component list proxies may return snapshot JSON with `_readCache.stale=true`; UI shows a read-only banner. With `deploy-mode=cluster`, Redis implementation is used automatically. See [EXECUTOR_READ_CACHE.md](../guides/EXECUTOR_READ_CACHE.md).

### zestflow.collector.* (Admin side)

| Property | Default | Description |
|----------|---------|-------------|
| `api-url` | `http://localhost:20650` | Collector query fallback URL |
| `access-token` | empty | Must match Collector |

### zestflow.sso.* (Admin SSO)

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `false` | SSO master switch |
| `provider` | `zest-sso` | `zest-sso` / `oidc` / `none` |
| `display-name` | empty | Login button label for `oidc` (default `SSO`) |
| `issuer` | `http://localhost:9000` | IdP issuer |
| `discovery-uri` | ZestSSO discovery URL | Preferred over static endpoints |
| `client-id` | `zestflow-admin` | OAuth client ID |
| `client-secret` | placeholder | **Must change in production** |
| `redirect-uri` | `http://localhost:5173/login/callback` | Must match IdP registration |
| `jwks-uri` | `{issuer}/oauth2/jwks` | Used when discovery is absent |
| `scopes` | openid,profile,email,roles,tenant | Space or comma separated |
| `post-logout-redirect-uri` | login page URL | SLO redirect |
| `claims.username-claim` | `preferred_username` | JWT username |
| `claims.roles-claim` | `roles` | Role list claim |
| `claims.admin-role` | `SSO_ADMIN` | Maps to super admin |
| `zest-sso.use-logout-url-api` | `true` | Call ZestSSO `/api/public/logout-url` |
| `zest-sso.logout-url-api-path` | `/api/public/logout-url` | Logout URL API path |

Cluster: `deploy-mode=cluster` stores PKCE state in Redis; configure `spring.data.redis.*`.

See [SSO_INTEGRATION.md](../guides/SSO_INTEGRATION.md).

### zestflow.jwt.*

| Property | Default | Description |
|----------|---------|-------------|
| `secret` | Dev default | **Must change in production**, ≥32 characters |
| `expiration` | `86400000` | Token validity (milliseconds) |

### zestflow.mail.*

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `false` | SMTP switch |
| `base-url` | — | Email link base URL |

---

## Port Quick Reference

| Port | Component | Config Key |
|------|-----------|------------|
| 8080 | Admin | `server.port` |
| 8081 | Demo business app | `server.port` |
| 20550 | Executor Netty | `zestflow.executor.port` |
| 20650 | Collector Netty | `zestflow.collector.registry.port` |

---

## Environment Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local development, `application-local.yml` |
| `prod` | Production; `ProductionSecretGuard` enforces secrets |
| `demo` | Public demo package |

---

## Related Documentation

- [DEPLOY.md](../DEPLOY.en.md) — Production secret generation and deployment
- [ARCHITECTURE.md](../ARCHITECTURE.en.md) §11 — Architecture-level configuration
- [QUICK_REFERENCE.en.md](../QUICK_REFERENCE.en.md) — Annotations and API quick reference
- [SPI.en.md](SPI.en.md) — SPI-related configuration
