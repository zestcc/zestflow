# 配置参考

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](CONFIGURATION.en.md)
> **源码权威来源**：各模块 `application.yml` + `*Properties.java`

本文档汇总 ZestFlow 主要配置前缀。业务项目只需覆盖 `zestflow.executor.*` 与 `zestflow.collector.*`；Admin 部署见 `zestflow.admin.*`。

---

## 配置同步规范

修改 `zestflow.executor.*` 时须同步：

- `zestflow-executor/src/main/resources/application.yml`
- `zestflow-demo/src/main/resources/application.yml`
- `zestflow-demo/src/main/resources/application-prod.example.yml`
- `zestflow-demo/src/test/resources/application-test.yml`

修改 `zestflow.admin.*` 时须同步：

- `zestflow-admin/src/main/resources/application.yml`
- `zestflow-admin/src/main/resources/application-prod.example.yml`

---

## zestflow.executor.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `app-code` | `spring.application.name` | 应用编码 |
| `app-name` | 同 app-code | 展示名称 |
| `admin-addresses` | `http://localhost:8080` | Admin 地址，逗号分隔 |
| `access-token` | 空 | Netty `X-Access-Token`，与 Admin `executor-access-token` 一致 |
| `registry-token` | 空 | 注册/心跳 `X-Registry-Token` |
| `heartbeat-interval` | `30` | 心跳间隔（秒） |
| `host` | 自动探测内网 IPv4 | Executor 注册 Host |
| `port` | `20550` | Netty 服务端口 |
| `execute-endpoint-enabled` | `false` | Tomcat 是否暴露 `/execute` |
| `chain-route-enabled` | `false` | Mode 2 链 HTTP 路由 |
| `execute-response-mode` | `BODY` | Tomcat 成功响应：BODY / DETAIL |
| `execute-failure-policy` | `PROPAGATE` | PROPAGATE / ERROR_HANDLER / WRAPPED |
| `timeout-ms` | `5000` | 注册/心跳 HTTP 超时 |
| `tenant-id` | `1` | 租户 ID |
| `data-dir` | `./zestflow-data` | 链/设计/AI 知识库目录 |
| `idempotency-enabled` | `true` | `/execute` 幂等去重 |
| `idempotency-ttl-ms` | `300000` | 幂等缓存 TTL |
| `shard-index` / `shard-total` | `0` / `1` | Cron 分片 |
| `ai-localhost-only` | `true` | 无 accessToken 时 AI API 仅本机 |

### zestflow.executor.chain.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `load-retry-times` | `3` | 链加载重试 |
| `load-retry-interval-ms` | `5000` | 重试间隔 |
| `auto-reload` | `true` | 热更新 |
| `reload-check-interval-ms` | `60000` | 热更新轮询间隔 |
| `parallel-threshold` | `3` | 同层并行上限 |
| `default-timeout-ms` | `60000` | 链默认超时 |
| `node-default-retry-count` | `0` | 节点默认重试次数 |
| `declaration-sync-enabled` | `true` | `@ZestChain` 占位同步 |

### zestflow.executor.expression.*

Aviator 表达式引擎（边条件 / SCRIPT 节点 / While 循环）。详见 [CHAIN_ORCHESTRATION.md](../guides/CHAIN_ORCHESTRATION.md) §10。

| 属性 | 默认 | 说明 |
|------|------|------|
| `timeout-ms` | `5000` | 单次求值/脚本超时（毫秒） |
| `max-script-length` | `10000` | 表达式最大字符数 |
| `max-cache-size` | `1000` | 编译缓存 LRU 上限 |
| `max-loop-count` | `10000` | Aviator 循环次数上限 |
| `condition-fail-open` | `false` | 条件失败是否视为 true（默认 fail-closed） |
| `clear-cache-on-chain-reload` | `true` | 链热加载后清编译缓存 |

### zestflow.executor.event.*

由 `collector-jdbc` 绑定，常用项：

| 属性 | 默认 | 说明 |
|------|------|------|
| `queue-capacity` | `8192` | 有界队列容量 |
| `batch-size` | `200` | 批量落库大小 |
| `batch-max-wait-ms` | `500` | 批量等待上限 |
| `circuit-breaker-threshold` | `10` | 熔断连续失败次数 |
| `disk-fallback-enabled` | `false` | 磁盘降级 |

### zestflow.executor.schedule.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `enabled` | `true` | Executor 侧调度开关 |
| `driver` | `embedded` | `embedded` / `xxl-job` / `noop` |
| `poll-interval-ms` | `15000` | 调度扫描间隔 |

### zestflow.executor.ai.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `llm-enabled` | `false` | Executor 侧 LLM suggest |
| `base-url` | `http://localhost:11434/v1` | OpenAI 兼容 API |
| `model` | `llama3.2` | 模型名 |
| `rag-mode` | `hybrid` | RAG 检索模式 |
| `temperature` | `0.2` | 生成温度 |
| `repair-max-rounds` | `2` | validate 失败修复轮次 |

详见 [adr/SCHEDULING.md](../adr/SCHEDULING.md)、[AI_CHAIN_LEARNING.md](../AI_CHAIN_LEARNING.md)。

---

## zestflow.collector.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `datasource.url` | — | 日志库 JDBC URL |
| `registry.port` | `9998`（模块默认）；Demo/生产推荐 `20650` | Collector Netty 端口 |
| `registry.admin-addresses` | 同 executor | Admin 注册地址 |
| `access-token` | 空 | Admin 查询 `X-Collector-Token` |
| `batch-size` | `200` | 批量写入大小 |
| `queue-capacity` | `8192` | 异步队列容量 |
| `async-enabled` | `true` | 异步采集开关 |

Kafka / RabbitMQ 实现见 `zestflow.collector.kafka.topic`、`zestflow.collector.rabbitmq.exchange`。

---

## zestflow.admin.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `registry-token` | 空 | 机器接口令牌（生产必填） |
| `executor-access-token` | 空 | Admin → Executor Netty |
| `deploy-mode` | `standalone` | standalone / cluster |
| `protocol` | `http` | 内部通信协议 |
| `http-timeout-ms` | `5000` | HTTP 客户端超时 |
| `cache.type` | `caffeine` | simple / caffeine / redis |

### zestflow.collector.*（Admin 侧）

| 属性 | 默认 | 说明 |
|------|------|------|
| `api-url` | `http://localhost:20650` | Collector 查询兜底地址 |
| `access-token` | 空 | 与 Collector 一致 |

### zestflow.sso.*（Admin SSO）

| 属性 | 默认 | 说明 |
|------|------|------|
| `enabled` | `false` | SSO 总开关 |
| `provider` | `zest-sso` | `zest-sso` / `oidc` / `none` |
| `display-name` | 空 | `oidc` 时登录按钮文案，默认 `SSO` |
| `issuer` | `http://localhost:9000` | IdP Issuer |
| `discovery-uri` | ZestSSO Discovery URL | 优先于静态端点 |
| `client-id` | `zestflow-admin` | OAuth 客户端 ID |
| `client-secret` | 模板占位符 | **生产须更换** |
| `redirect-uri` | `http://localhost:5173/login/callback` | 与 IdP 注册一致 |
| `jwks-uri` | `{issuer}/oauth2/jwks` | 无 Discovery 时使用 |
| `scopes` | openid,profile,email,roles,tenant | 空格或逗号分隔 |
| `post-logout-redirect-uri` | 登录页 URL | SLO 回跳地址 |
| `claims.username-claim` | `preferred_username` | JWT 用户名 |
| `claims.roles-claim` | `roles` | 角色列表 |
| `claims.admin-role` | `SSO_ADMIN` | 映射超管 |
| `zest-sso.use-logout-url-api` | `true` | 调用 ZestSSO `/api/public/logout-url` |
| `zest-sso.logout-url-api-path` | `/api/public/logout-url` | 登出 URL API 路径 |

集群：`deploy-mode=cluster` 时 PKCE state 走 Redis（`SsoPkceStore`），须配置 `spring.data.redis.*`。

详见 [SSO_INTEGRATION.md](../guides/SSO_INTEGRATION.md)。

### zestflow.jwt.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `secret` | 开发默认值 | **生产必须更换**，≥32 字符 |
| `expiration` | `86400000` | Token 有效期（毫秒） |

### zestflow.mail.*

| 属性 | 默认 | 说明 |
|------|------|------|
| `enabled` | `false` | SMTP 开关 |
| `base-url` | — | 邮件链接基址 |

---

## 端口速查

| 端口 | 组件 | 配置项 |
|------|------|--------|
| 8080 | Admin | `server.port` |
| 8081 | Demo 业务 | `server.port` |
| 20550 | Executor Netty | `zestflow.executor.port` |
| 20650 | Collector Netty | `zestflow.collector.registry.port` |

---

## 环境 Profile

| Profile | 用途 |
|---------|------|
| `local` | 本地开发，`application-local.yml` |
| `prod` | 生产，`ProductionSecretGuard` 强制密钥 |
| `demo` | 公网试玩包 |

---

## 相关文档

- [DEPLOY.md](../DEPLOY.md) — 生产密钥生成与部署
- [ARCHITECTURE.md](../ARCHITECTURE.md) §11 — 架构级配置说明
- [QUICK_REFERENCE.md](../QUICK_REFERENCE.md) — 注解与 API
