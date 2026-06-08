# API Reference

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](API.md)  
> **OpenAPI 3 (Admin REST, auto-generated):** [OPENAPI.en.md](OPENAPI.en.md) · [admin-api.json](../openapi/admin-api.json) · Swagger UI `http://localhost:8080/swagger-ui.html` (local profile only)

**Source of truth:** `zestflow-admin/.../controller/`, `ServerHandler.java`, `CollectorServerHandler.java`

---

## Table of Contents

- [1. General Conventions](#1-general-conventions)
- [2. Executor Netty API (:20550)](#2-executor-netty-api-20550)
- [3. Registry API](#3-registry-api)
- [4. Admin REST API (/api/zestflow)](#4-admin-rest-api-apizestflow)
- [5. Collector Netty API (:20650)](#5-collector-netty-api-20650)
- [6. Appendix: Full Admin Endpoint Index](#6-appendix-full-admin-endpoint-index)

---

## 1. General Conventions

### 1.1 Response Envelope

| Channel | Success Response | Failure Response |
|---------|------------------|------------------|
| **Admin REST** | `Result<T>`: `{ "code": 200, "message": "success", "data": ... }` | `code` ≠ 200; `message` describes the reason |
| **Executor Netty `/execute`** | HTTP 200 + full `ChainExecuteResultDTO` (including failed chains) | HTTP 500 + `{ "code": 500, "message": "..." }` |
| **Chain / Design / Component proxy** | Mostly returns raw Executor JSON string | Same as Executor side |
| **Collector Netty** | JSON business body (no unified `Result` wrapper) | HTTP 4xx/5xx |

### 1.2 Authentication

| Scenario | Header | Notes |
|----------|--------|-------|
| Admin user API | `Authorization: Bearer <JWT>` | Obtain via `/auth/login` |
| Executor / Collector registration | `X-Registry-Token` | Must match `zestflow.admin.registry-token` |
| Admin → Executor Netty | `X-Access-Token` | Must match `zestflow.executor.access-token` |
| Admin → Collector | `X-Collector-Token` | Must match `zestflow.collector.access-token` |
| Executor AI `/api/ai/*` | No token | Defaults to localhost-only (`aiLocalhostOnly=true`) |

### 1.3 Path Prefixes

| Service | Base URL | Example |
|---------|----------|---------|
| Admin REST | `http://localhost:8080/api/zestflow` | `/chains`, `/logs/events/query` |
| Executor Netty | `http://localhost:20550` | `/execute`, `/api/chains` |
| Collector Netty | `http://localhost:20650` | `/collector/events/query` |

---

## 2. Executor Netty API (:20550)

### 2.1 POST `/execute` — Execute Chain (Core)

**Purpose:** Primary channel for Admin Playground, schedule callbacks, and direct business-side chain invocation.

**Authentication:** Optional `X-Access-Token` (required in production)

**Request body:** `ChainExecuteRequestDTO` (JSON)

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `chainCode` | string | One of chainCode / chainKey | Chain code, e.g. `CHN20260529000001` |
| `chainKey` | string | One of chainCode / chainKey | Stable chain identifier; **takes precedence over chainCode** |
| `params` | object | No | Input map injected into DataBus / `@ZestParam` |
| `source` | string | No | Source identifier (for logging) |
| `timeoutMs` | long | No | Override chain default timeout |
| `traceId` | string | No | Trace ID; can serve as idempotency key fallback |
| `idempotencyKey` | string | No | Idempotency key (takes precedence over traceId) |
| `headers` | object | No | HTTP headers forwarded to `ChainContext` |

**Request example:**

```bash
curl -X POST http://localhost:20550/execute \
  -H "Content-Type: application/json" \
  -H "X-Access-Token: your-token" \
  -d '{
    "chainCode": "CHN20260529000001",
    "params": {
      "userId": "U001",
      "amount": 99.9
    },
    "source": "API"
  }'
```

**Response:** HTTP **200** + `ChainExecuteResultDTO` (**fixed DETAIL mode**; not affected by `execute-response-mode`)

| Field | Type | Description |
|-------|------|-------------|
| `instanceId` | string | Execution instance ID (trace correlation in logs) |
| `chainCode` | string | Chain code |
| `status` | int | Chain status; see [Status Codes](#23-status-codes) |
| `costMs` | long | Total duration (milliseconds) |
| `resultData` | object | DataBus snapshot |
| `nodeResults` | array | Per-node `NodeResultDTO` entries |
| `finalReturnValue` | any | Chain final return value (typically PARSER output) |
| `errorMessage` | string | Error message on failure |
| `errorCode` | string | Business error code |
| `failedNodeId` | string | Failed node ID |

**NodeResultDTO fields:**

| Field | Type | Description |
|-------|------|-------------|
| `nodeId` | string | Designer node ID |
| `status` | int | Node status code |
| `costMs` | long | Node duration |
| `outputData` | object | Data written to DataBus |
| `returnValue` | any | Raw component method return value |
| `errorMessage` | string | Node error message |
| `retryCount` | int | Retry count |

**Important notes:**

1. Chain execution failures still return **HTTP 200**; determine failure via `status=5` (`CHAIN_FAILED`).
2. Admin Playground depends on `instanceId` and `nodeResults`; **do not** use the Tomcat `executeHttp()` path for this endpoint.
3. When `idempotencyEnabled` is on, the same `idempotencyKey` within TTL returns the same result.

### 2.2 GET `/health`

Health check; returns JSON status.

### 2.3 Status Codes

**Chain runtime status (`ChainExecuteResultDTO.status`):**

| Value | Constant | Meaning |
|-------|----------|---------|
| 3 | `CHAIN_RUNNING` | Running |
| 4 | `CHAIN_SUCCESS` | Success |
| 5 | `CHAIN_FAILED` | Failed |
| 6 | `CHAIN_TIMEOUT` | Timed out |
| 8 | `CHAIN_STOPPED` | Stopped |

**Node status (`NodeResultDTO.status`):**

| Value | Constant | Meaning |
|-------|----------|---------|
| 2 | `NODE_RUNNING` | Running |
| 3 | `NODE_SUCCESS` | Success |
| 4 | `NODE_FAILED` | Failed |
| 8 | `NODE_SKIPPED` | Skipped |

See `com.zestflow.common.constant.ChainConstants` for the full set of constants.

### 2.4 Chain / Design / Component CRUD

Admin proxies the following paths via `ExecutorProxyService` (Executor accepts the same paths locally):

| Resource | Base Path | Main Operations |
|----------|-----------|-----------------|
| Chain | `/api/chains` | GET list, POST create, PUT update, DELETE delete, POST validate-definition, PUT reload |
| Design | `/api/designs` | GET/POST/PUT/DELETE, PUT `{code}/graph` to save X6 graph |
| Component | `/api/components` | GET list, POST refresh to rescan |
| Schedule | `/api/schedules` | CRUD + POST `{id}/trigger` for manual trigger |
| AI | `/api/ai/*` | RAG, suggest, learning events (see [AI_COPILOT.md](../AI_COPILOT.en.md)) |

---

## 3. Registry API

### 3.1 Executor Registration (Admin)

**Base path:** `/api/zestflow/registry` · **No JWT required**

| Method | Path | Request Body | Description |
|--------|------|--------------|-------------|
| POST | `/register` | `RegisterDTO` | Register / update Executor |
| POST | `/heartbeat` | `HeartbeatDTO` | Heartbeat (default 30s interval) |
| DELETE | `/{executorId}` | — | Deregister |
| PUT | `/{executorId}/status` | `status` query param | Update status |

**RegisterDTO key fields:** `executorId`, `host`, `port`, `moduleCode`, `moduleName`

### 3.2 Collector Registration

**Base path:** `/api/zestflow/registry/collector`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/register` | Register Collector |
| POST | `/heartbeat` | Heartbeat |
| DELETE | `/{collectorId}` | Deregister |

---

## 4. Admin REST API (/api/zestflow)

### 4.1 Authentication `/auth`

| Method | Path | Description |
|--------|------|-------------|
| POST | `/login` | Login → `LoginVO` (includes JWT) |
| POST | `/register` | Register |
| GET | `/userinfo` | Current user |
| POST | `/switch-tenant/{id}` | Switch tenant (new JWT) |
| PUT | `/force-password` | Force password change |

### 4.2 Chain Management `/chains` (proxied to Executor)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Paginated list (query: `appCode`, `page`, `size`) |
| GET | `/code/{code}` | Full definition (includes graphData) |
| POST | `/` | Create chain (code auto-generated as `CHN*`) |
| PUT | `/{code}` | Update |
| POST | `/{code}/publish` | **Publish** to all online Executors |
| POST | `/{code}/rollback/{version}` | Roll back to version |
| POST | `/sync` | Executor chain sync report (**no JWT required**) |

**Publishing notes:** Confirm target Executors are online and components are scanned before publishing; multi-instance deployments broadcast reload.

### 4.3 Log Query `/logs`

#### POST `/logs/events/query`

**Request body:** `EventQuery`

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `executionId` | string | — | Execution trace ID |
| `chainId` | string | — | Chain code |
| `appCode` | string | — | Application code |
| `executorId` | string | — | Executor ID |
| `eventTypes` | array | — | e.g. `CHAIN_STARTED`, `NODE_COMPLETED` |
| `startTime` / `endTime` | long | — | Millisecond timestamps |
| `status` | int | — | 0=failed, 1=success |
| `keyword` | string | — | Fuzzy match on chain name / node name |
| `page` | int | 1 | Page number |
| `pageSize` | int | 20 | Page size |

**Response:** `Result<PageResult<EventQueryResult>>`

#### POST `/logs/executions`

Paginated execution traces; same parameters as `EventQuery`.

#### GET `/logs/executions/{executionId}/nodes/{nodeId}`

Single-node input/output detail. Optional query: `nodeShape`, `appCode`.

#### POST `/logs/analytics/*`

Statistics, trends, rankings, failure clusters, etc. Request body: `LogAnalyticsQuery` / `EventStatsQuery`.

### 4.4 Playground `/playground` (requires `zestflow.playground.enabled=true`)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/scene/{sceneCode}` | Scene info and default request template |
| POST | `/execute/{sceneCode}` | Execute scene (internally uses Netty `/execute`) |
| POST | `/history` | Current user's execution history |

### 4.5 Scheduling `/schedules`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Paginated list |
| POST | `/` | Create (`ScheduleCreateDTO`) |
| PUT | `/{id}/status` | Enable / disable |
| POST | `/{id}/trigger` | Manual trigger |
| GET | `/logs` | Schedule logs |

> **Architecture note:** Business cron jobs are executed autonomously by Executor `EmbeddedScheduleDriver` reading the business database. Admin-side scheduling is the platform governance layer. See [adr/SCHEDULING.md](../adr/SCHEDULING.en.md).

### 4.6 AI Copilot `/ai`

32 endpoints covering configuration, RAG, design suggest/validate, log diagnose, learning events, and more. Full list in [AI_COPILOT.md](../AI_COPILOT.en.md) Appendix B.

---

## 5. Collector Netty API (:20650)

**Authentication:** All endpoints except `/collector/health` require `X-Collector-Token` (validation skipped when token is not configured)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/collector/health` | Health + metrics |
| POST | `/collector/events/query` | Paginated events (body: `EventQuery`) |
| POST | `/collector/events/stats` | Statistics |
| POST | `/collector/events/executions` | Execution traces |
| GET | `/collector/events/{eventId}` | Single event |
| GET | `/collector/events/executions/{executionId}` | Trace detail |
| GET | `/collector/events/executions/{executionId}/nodes/{nodeId}` | Node detail |
| POST | `/collector/events/ingest` | Remote batch ingest |
| POST | `/collector/snapshots` | Save chain graph snapshot |
| GET | `/collector/snapshots` | Query snapshot by timestamp |

Admin log pages aggregate online Collectors via `CollectorClient`, with fallback to `zestflow.collector.api-url`.

---

## 6. Appendix: Full Admin Endpoint Index

**24** `@RestController` classes, approximately **161** endpoints. Grouped by controller:

| Controller | Prefix | Endpoints |
|------------|--------|-----------|
| AuthController | `/auth` | 13 |
| RegistryController | `/registry` | 4 |
| CollectorRegistryController | `/registry/collector` | 3 |
| ChainController | `/chains` | 13 |
| DesignController | `/designs` | 11 |
| ComponentController | `/components` | 2 |
| LogController | `/logs` | 11 |
| ExecutorController | `/executors` | 8 |
| ScheduleController | `/schedules` | 9 |
| AiCopilotController | `/ai` | 32 |
| Playground* | `/playground` | 15 |
| UserManageController | `/users` | 8 |
| TenantController | `/tenants` | 5 |
| AlertController | `/alerts` | 5 |
| DictTypeController | `/dict-types` | 11 |
| SysConfigController | `/sys-configs` | 7 |
| DashboardController | `/dashboard` | 1 |

---

## Related Documentation

- [ANNOTATIONS.en.md](ANNOTATIONS.en.md) — Component annotations
- [CONFIGURATION.en.md](CONFIGURATION.en.md) — Configuration properties
- [EXECUTION_ENGINE.en.md](EXECUTION_ENGINE.en.md) — Programmatic execution
- [FAQ.en.md](FAQ.en.md) — Frequently asked questions
- [OPENAPI.en.md](OPENAPI.en.md) — OpenAPI usage guide
