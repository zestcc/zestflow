# 架构决策记录（ADR）索引

> **版本** 0.1.0 · **更新** 2026-06-08 · **语言** 简体中文 · [English](README.en.md) · [← 文档中心](../README.md)

架构决策记录（ADR）沉淀 ZestFlow 的重要设计选择，说明背景、决策与后果。与完整架构文档 [ARCHITECTURE.md](../ARCHITECTURE.md) 互补。

---

## 文档列表

### [SCHEDULING.md](SCHEDULING.md)

**状态**：已采纳 · **版本**：0.2

在 Hub 控制面 + 业务数据面分离模型下，定义 ZestFlow **调度架构**。核心决策：Admin 不再扫描业务 Cron（移除 `admin.schedule.scan`）；链调度存业务库（`zf_schedule` / `zf_schedule_log`）；Executor 默认 **EmbeddedScheduleDriver** 本地 Cron、分片、进程内执行链；SLA 扫描下沉 Collector；注册离线检测改为事件驱动。Admin 保留平台 Cron（租户清理等）及调度 CRUD 的 HTTP 代理。

**英文版**：[SCHEDULING.en.md](SCHEDULING.en.md)

### [SCHEDULING_SPI_XXLJOB.md](SCHEDULING_SPI_XXLJOB.md)

**状态**：已采纳 · **版本**：0.1.0

说明通过 **xxl-job** 接入外部调度，作为内嵌调度的替代方案。涵盖 `ScheduleDriver` SPI 切换（`embedded` | `noop` | `external`）、xxl-job Admin 地址 YAML 配置、内置 `zestflowChainJob` Handler 委托 `ChainExecuteFacade.executeCore`、幂等键，以及未使用 xxl-job 时的 HTTP `/execute` 兜底。Hub 调度 UI 在内嵌模式（读写业务库）与 external 模式（Cron 在 xxl-job Admin 配置）下行为不同。

**英文版**：[SCHEDULING_SPI_XXLJOB.en.md](SCHEDULING_SPI_XXLJOB.en.md)

---

## 相关文档

| 主题 | 文档 |
|------|------|
| 架构文档调度章节 | [ARCHITECTURE.md §5.5.4](../ARCHITECTURE.md) |
| 调度验收用例 | [acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](../acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md) |
| 部署 | [DEPLOY.md](../DEPLOY.md) |
