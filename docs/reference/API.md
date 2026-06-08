# API 参考

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](API.en.md)
> **OpenAPI 3（Admin REST 自动生成）**：[OPENAPI.md](OPENAPI.md) · [admin-api.json](../openapi/admin-api.json) · Swagger UI `http://localhost:8080/swagger-ui.html`（仅 local）

**源码权威：** `zestflow-admin/.../controller/`、`ServerHandler.java`、`CollectorServerHandler.java`

---

## 目录

- [1. 通用约定](#1-通用约定)
- [2. Executor Netty API（:20550）](#2-executor-netty-api20550)
- [3. 注册 API](#3-注册-api)
- [4. Admin REST API（/api/zestflow）](#4-admin-rest-apiapizestflow)
- [5. Collector Netty API（:20650）](#5-collector-netty-api20650)
- [6. 附录：Admin 端点全量索引](#6-附录admin-端点全量索引)

---

## 1. 通用约定

### 1.1 响应包装

| 通道 | 成功响应 | 失败响应 |
|------|---------|---------|
| **Admin REST** | `Result<T>`：`{ "code": 200, "message": "success", "data": ... }` | `code` ≠ 200，`message` 描述原因 |
| **Executor Netty `/execute`** | HTTP 200 + 完整 `ChainExecuteResultDTO`（含失败链） | HTTP 500 + `{ "code": 500, "message": "..." }` |
| **链/设计/元件代理** | 多数返回 Executor 原始 JSON 字符串 | 同 Executor 侧 |
| **Collector Netty** | JSON 业务体（无统一 Result 包装） | HTTP 4xx/5xx |

### 1.2 鉴权

| 场景 | 请求头 | 说明 |
|------|--------|------|
| Admin 用户 API | `Authorization: Bearer <JWT>` | 登录 `/auth/login` 获取 |
| Executor/Collector 注册 | `X-Registry-Token` | 与 `zestflow.admin.registry-token` 一致 |
| Admin → Executor Netty | `X-Access-Token` | 与 `zestflow.executor.access-token` 一致 |
| Admin → Collector | `X-Collector-Token` | 与 `zestflow.collector.access-token` 一致 |
| Executor AI `/api/ai/*` | 无 Token 时 | 默认仅允许本机（`aiLocalhostOnly=true`） |

### 1.3 路径前缀

| 服务 | 基址 | 示例 |
|------|------|------|
| Admin REST | `http://localhost:8080/api/zestflow` | `/chains`、`/logs/events/query` |
| Executor Netty | `http://localhost:20550` | `/execute`、`/api/chains` |
| Collector Netty | `http://localhost:20650` | `/collector/events/query` |

---

## 2. Executor Netty API（:20550）

### 2.1 POST `/execute` — 执行链（核心）

**用途：** Admin 试验场、调度回调、业务方直接调链的主通道。

**鉴权：** 可选 `X-Access-Token`（生产必填）

**请求体：** `ChainExecuteRequestDTO`（JSON）

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `chainCode` | string | 与 chainKey 二选一 | 链编码，如 `CHN20260529000001` |
| `chainKey` | string | 与 chainCode 二选一 | 稳定链标识；**优先于 chainCode** |
| `params` | object | 否 | 入参 Map，注入 DataBus / `@ZestParam` |
| `source` | string | 否 | 来源标识（日志用） |
| `timeoutMs` | long | 否 | 覆盖链默认超时 |
| `traceId` | string | 否 | 追踪 ID；可作幂等键 fallback |
| `idempotencyKey` | string | 否 | 幂等键（优先于 traceId） |
| `headers` | object | 否 | HTTP 头透传至 `ChainContext` |

**请求示例：**

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

**响应：** HTTP **200** + `ChainExecuteResultDTO`（**固定 DETAIL 模式**，不受 `execute-response-mode` 影响）

| 字段 | 类型 | 说明 |
|------|------|------|
| `instanceId` | string | 执行实例 ID（日志 trace 关联） |
| `chainCode` | string | 链编码 |
| `status` | int | 链状态，见 [状态码](#23-状态码) |
| `costMs` | long | 总耗时（毫秒） |
| `resultData` | object | DataBus 快照 |
| `nodeResults` | array | 各节点 `NodeResultDTO` |
| `finalReturnValue` | any | 链终态返回值（通常 PARSER 输出） |
| `errorMessage` | string | 失败时错误信息 |
| `errorCode` | string | 业务错误码 |
| `failedNodeId` | string | 失败节点 ID |

**NodeResultDTO 字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `nodeId` | string | 设计器节点 ID |
| `status` | int | 节点状态码 |
| `costMs` | long | 节点耗时 |
| `outputData` | object | 写入 DataBus 的数据 |
| `returnValue` | any | 元件方法原始返回值 |
| `errorMessage` | string | 节点错误 |
| `retryCount` | int | 重试次数 |

**注意事项：**

1. 链执行失败时仍返回 **HTTP 200**，通过 `status=5`（`CHAIN_FAILED`）判定。
2. Admin 试验场依赖 `instanceId` 与 `nodeResults`，**禁止**对此端点使用 Tomcat `executeHttp()` 路径。
3. 启用 `idempotencyEnabled` 时，相同 `idempotencyKey` 在 TTL 内返回同一结果。

### 2.2 GET `/health`

健康检查，返回 JSON 状态。

### 2.3 状态码

**链运行状态（`ChainExecuteResultDTO.status`）：**

| 值 | 常量 | 含义 |
|----|------|------|
| 3 | `CHAIN_RUNNING` | 执行中 |
| 4 | `CHAIN_SUCCESS` | 成功 |
| 5 | `CHAIN_FAILED` | 失败 |
| 6 | `CHAIN_TIMEOUT` | 超时 |
| 8 | `CHAIN_STOPPED` | 已停止 |

**节点状态（`NodeResultDTO.status`）：**

| 值 | 常量 | 含义 |
|----|------|------|
| 2 | `NODE_RUNNING` | 执行中 |
| 3 | `NODE_SUCCESS` | 成功 |
| 4 | `NODE_FAILED` | 失败 |
| 8 | `NODE_SKIPPED` | 跳过 |

完整常量见 `com.zestflow.common.constant.ChainConstants`。

### 2.4 链 / 设计 / 元件 CRUD

Admin 通过 `ExecutorProxyService` 代理以下路径（Executor 本地直连相同路径）：

| 资源 | 基路径 | 主要操作 |
|------|--------|---------|
| 链 | `/api/chains` | GET 列表、POST 创建、PUT 更新、DELETE 删除、POST validate-definition、PUT reload |
| 设计 | `/api/designs` | GET/POST/PUT/DELETE、PUT `{code}/graph` 保存 X6 图 |
| 元件 | `/api/components` | GET 列表、POST refresh 重新扫描 |
| 调度 | `/api/schedules` | CRUD + POST `{id}/trigger` 手动触发 |
| AI | `/api/ai/*` | RAG、suggest、learning events（见 [AI_COPILOT.md](../AI_COPILOT.md)） |

---

## 3. 注册 API

### 3.1 Executor 注册（Admin）

**基路径：** `/api/zestflow/registry` · **无需 JWT**

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| POST | `/register` | `RegisterDTO` | 注册/更新 Executor |
| POST | `/heartbeat` | `HeartbeatDTO` | 心跳（默认 30s 间隔） |
| DELETE | `/{executorId}` | — | 注销 |
| PUT | `/{executorId}/status` | `status` query | 更新状态 |

**RegisterDTO 主要字段：** `executorId`、`host`、`port`、`moduleCode`、`moduleName`

### 3.2 Collector 注册

**基路径：** `/api/zestflow/registry/collector`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/register` | Collector 注册 |
| POST | `/heartbeat` | 心跳 |
| DELETE | `/{collectorId}` | 注销 |

---

## 4. Admin REST API（/api/zestflow）

### 4.1 认证 `/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 登录 → `LoginVO`（含 JWT） |
| POST | `/register` | 注册 |
| GET | `/userinfo` | 当前用户 |
| POST | `/switch-tenant/{id}` | 切换租户（新 JWT） |
| PUT | `/force-password` | 强制改密 |

### 4.2 链管理 `/chains`（代理 Executor）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页列表（query: `appCode`, `page`, `size`） |
| GET | `/code/{code}` | 完整定义（含 graphData） |
| POST | `/` | 创建链（编码自动生成 `CHN*`） |
| PUT | `/{code}` | 更新 |
| POST | `/{code}/publish` | **发布**到所有在线 Executor |
| POST | `/{code}/rollback/{version}` | 版本回滚 |
| POST | `/sync` | Executor 链同步上报（**无需 JWT**） |

**发布注意事项：** 发布前确认目标 Executor 在线且元件已扫描；多实例时广播 reload。

### 4.3 日志查询 `/logs`

#### POST `/logs/events/query`

**请求体：** `EventQuery`

| 字段 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `executionId` | string | — | 执行追踪 ID |
| `chainId` | string | — | 链编码 |
| `appCode` | string | — | 应用编码 |
| `executorId` | string | — | 执行器 ID |
| `eventTypes` | array | — | `CHAIN_STARTED`、`NODE_COMPLETED` 等 |
| `startTime` / `endTime` | long | — | 毫秒时间戳 |
| `status` | int | — | 0=失败，1=成功 |
| `keyword` | string | — | 模糊匹配链名/节点名 |
| `page` | int | 1 | 页码 |
| `pageSize` | int | 20 | 每页条数 |

**响应：** `Result<PageResult<EventQueryResult>>`

#### POST `/logs/executions`

执行轨迹分页，参数同 `EventQuery`。

#### GET `/logs/executions/{executionId}/nodes/{nodeId}`

单节点入参/出参详情。可选 query：`nodeShape`、`appCode`。

#### POST `/logs/analytics/*`

统计、趋势、排行、失败聚类等，请求体 `LogAnalyticsQuery` / `EventStatsQuery`。

### 4.4 试验场 `/playground`（需 `zestflow.playground.enabled=true`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/scene/{sceneCode}` | 场景信息与默认请求模板 |
| POST | `/execute/{sceneCode}` | 执行场景（内部走 Netty `/execute`） |
| POST | `/history` | 当前用户执行历史 |

### 4.5 调度 `/schedules`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 分页列表 |
| POST | `/` | 创建（`ScheduleCreateDTO`） |
| PUT | `/{id}/status` | 启停 |
| POST | `/{id}/trigger` | 手动触发 |
| GET | `/logs` | 调度日志 |

> **架构说明：** 业务 Cron 由 Executor `EmbeddedScheduleDriver` 读业务库自治执行，Admin 侧调度为平台治理层。详见 [adr/SCHEDULING.md](../adr/SCHEDULING.md)。

### 4.6 AI Copilot `/ai`

32 个端点，涵盖配置、RAG、设计 suggest/validate、日志 diagnose、learning events 等。完整列表见 [AI_COPILOT.md](../AI_COPILOT.md) 附录 B。

---

## 5. Collector Netty API（:20650）

**鉴权：** 除 `/collector/health` 外需 `X-Collector-Token`（未配置 token 则跳过校验）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/collector/health` | 健康 + metrics |
| POST | `/collector/events/query` | 事件分页（body: `EventQuery`） |
| POST | `/collector/events/stats` | 统计 |
| POST | `/collector/events/executions` | 执行轨迹 |
| GET | `/collector/events/{eventId}` | 单条事件 |
| GET | `/collector/events/executions/{executionId}` | 轨迹详情 |
| GET | `/collector/events/executions/{executionId}/nodes/{nodeId}` | 节点详情 |
| POST | `/collector/events/ingest` | 远程批量写入 |
| POST | `/collector/snapshots` | 保存链图快照 |
| GET | `/collector/snapshots` | 按时间点查快照 |

Admin 日志页通过 `CollectorClient` 聚合在线 Collector，兜底 `zestflow.collector.api-url`。

---

## 6. 附录：Admin 端点全量索引

共 **24** 个 `@RestController`、约 **161** 个端点。按控制器分组：

| 控制器 | 前缀 | 端点数 |
|--------|------|--------|
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

## 相关文档

- [ANNOTATIONS.md](ANNOTATIONS.md) — 元件注解
- [CONFIGURATION.md](CONFIGURATION.md) — 配置项
- [EXECUTION_ENGINE.md](EXECUTION_ENGINE.md) — 编程式执行
- [FAQ.md](FAQ.md) — 常见问题
