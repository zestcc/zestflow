# Changelog

> **语言** 简体中文 · [English](CHANGELOG.en.md)

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
