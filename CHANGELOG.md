# Changelog

> **语言** 简体中文 · [English](CHANGELOG.en.md)

## [1.0.0-SNAPSHOT] - 2026-06-13

### Added

- **StrictV1 门禁**：`scripts/blackbox/run-v1-acceptance.ps1`（全量 `mvn test` + cluster + npm build + 全 profile E2E + 严格 production-acceptance）
- 日志执行轨迹 **WebSocket** 传输（默认开启，前端 `streamExecutionTraceAuto` WS 优先、SSE 回退）
- 节点降级策略 `fallback.mode`：`default` / `constant` / `propagate`（无元件时生效，仍优先 `fallback.component`）
- v1.0 文档：[V1_0_ROADMAP.md](docs/V1_0_ROADMAP.md)、[MIGRATION_0.x_to_1.0.md](docs/MIGRATION_0.x_to_1.0.md)、[MULTI_TENANT.md](docs/guides/MULTI_TENANT.md)、[STRICT_V1_ACCEPTANCE.md](docs/guides/STRICT_V1_ACCEPTANCE.md)
- SSO/OIDC 企业登录（Authorization Code + PKCE）

### Changed

- 全模块 Maven 版本统一为 **1.0.0-SNAPSHOT**
- `zestflow.admin.log-live-stream.websocket-enabled` 默认 **true**
- `/system/features` 暴露 `logLiveStream.websocketEnabled`
- [ARCHITECTURE.md](docs/ARCHITECTURE.md) §8.5 声明 **1.0 API 稳定性**冻结范围

## [0.1.0] - 2026-06-03

### Added

- 公网部署指南 `docs/DEPLOY.md` 与 prod 配置模板
- Admin / Executor / Collector `prod` 启动守卫（`ProductionSecretGuard`）
- 试验场 `playground_record` MEDIUMTEXT 迁移与响应体软截断
- 链发布 `chainData` → `graphData` 回退
- E2E 全链路覆盖（32 功能探测 + 38 Playground 场景）

### Changed

- 全模块版本统一为 **0.1.0**
- Actuator 健康探测与 Admin 端口默认 8080
