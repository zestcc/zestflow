# ADR：ZestFlow 调度架构（Cron / 分片 / SPI）

> **状态**：已采纳 · **版本**：0.2 演进 · **更新**：2026-06-06 · [← 文档中心](../README.md) · [English](SCHEDULING.en.md)

## 1. 背景

ZestFlow 采用 **Hub 控制面 + 业务数据面分离**（三库：Admin / 业务 / 日志）。  
v0.1 业务链 Cron 由 Admin 每 15s 扫库并 HTTP 调 Executor（对标 xxl-job Admin），导致：

- Admin 停机 → 平台 Cron 停（与「Admin 不影响业务」冲突）
- 配置在 Admin 库，Executor 无法自治
- Admin 主动 fan-out（对账、SLA）随节点数变重

## 2. 决策摘要

| 层级 | 职责 |
|------|------|
| **Admin** | 平台自有 Cron（租户清理等）；链调度 **CRUD/查询代理**；**不**扫业务 Cron |
| **业务库** | `zf_schedule` / `zf_schedule_log` 为 **CHAIN 调度真源** |
| **Executor** | 默认 **EmbeddedScheduleDriver**：读业务库、本地 Cron、分片、进程内执行链 |
| **Collector** | 观测类 Cron 下沉（SLA 等，后续迭代） |
| **SPI** | `ScheduleDriver` 可替换为 xxl-job / HTTP 等外部触发 |

**不做**：Admin 集群 HA 作为业务 Cron 前提；内嵌完整 xxl-job Admin。

## 3. 任务归属

### 3.1 Admin 本地 Cron（`module=admin`, `remote=0`）

- 试玩租户回收 `admin.tenant.cleanup`
- 注册中心离线检测 / 清理（控制面）
- 链同步缓存淘汰（Admin 发布 UI）
- 平台级邮件告警（读 Admin 库的配置）

### 3.2 Executor 本地（`module=executor`, `remote=1`）

- 链热加载 `executor.chain.reload`
- 注册心跳 `executor.registry.heartbeat`
- **嵌入式链 Cron** `executor.schedule.embedded`（读 `zf_schedule`）

### 3.3 Collector 本地（`module=collector`, `remote=1`）

- 注册心跳
- **执行 SLA 扫描** `collector.alert.execution-sla`（读 chain_event，Admin 发信/冷却/历史）

### 3.4 已移除 / 事件驱动

- ~~`admin.schedule.scan`~~ — 业务 Cron 不再由 Admin 触发
- ~~`admin.registry.heartbeat-flush`~~ — 心跳事件同步 DB `last_heartbeat`
- ~~`admin.registry.offline-check`~~ — 心跳过期事件标记 ABNORMAL（`RegistryLifecycleService`）
- ~~`admin.alert.execution-sla`~~ — SLA 扫描下沉 Collector

## 4. 数据模型（业务库）

### `zf_schedule`

| 字段 | 说明 |
|------|------|
| `cron` | Cron 表达式 |
| `chain_code` / `chain_name` | 目标链 |
| `route_strategy` | `local`（默认）/ `round_robin` / `hash` — 手动触发时用 |
| `shard_total` | 分片总数，默认 1 |
| `shard_param` | 分片哈希键，默认 `schedule_id` |
| `params` | JSON 入参 |
| `status` | 0 停 / 1 启 |
| `tenant_id` / `app_code` | 租户与应用 |

### `zf_schedule_log`

Executor 触发后写入；Admin 通过 HTTP 代理查询。

### 分片

- **静态**：`shard_total` 在 `zf_schedule`
- **本机序号**：`zestflow.executor.shard-index` / `shard-total`（yml/env）
- **判定**：`hash(scheduleId) % shardTotal == shardIndex` 则本实例负责该任务

## 5. 触发流程（默认 Embedded）

```text
LocalScheduleEngine (Executor, 每 15s)
  → 读 zf_schedule (status=1)
  → 分片过滤
  → CronExpression 判断是否到期
  → ScheduleTriggerService.executeInProcess(chainCode, params, idempotencyKey)
  → 写 zf_schedule_log
```

**幂等键**：`schedule-{id}-cron-{fireEpochMs}`（与 v0.1 一致）

**手动触发**：Admin `POST /schedules/{id}/trigger` → 代理 Executor `POST /api/schedules/{id}/trigger`

## 6. Admin 代理（Hub）

与 `/chains` 相同模式：

```text
GET/POST/PUT/DELETE  /api/schedules  →  Executor Netty
GET                  /api/schedules/logs
POST                 /api/schedules/{id}/trigger
```

Admin 库 `schedule` 表 **仅保留 PLATFORM 任务**（调度中心展示平台内置任务）。

## 7. SPI：`ScheduleDriver`

```text
com.zestflow.common.spi.schedule.ScheduleDriver
  ├─ EmbeddedScheduleDriver   （默认，spring.factories / AutoConfig）
  ├─ NoopScheduleDriver       （schedule.enabled=false）
  └─ （扩展）XxlJobScheduleDriver / HttpCallbackScheduleDriver
```

配置：

```yaml
zestflow:
  executor:
    schedule:
      enabled: true
      driver: embedded   # embedded | noop | external（预留）
      poll-interval-ms: 15000
    shard-index: 0
    shard-total: 1
```

### 接 xxl-job（扩展，非默认实现）

1. **Executor 内 Handler**：xxl-job 回调 → `ChainExecuteFacade.executeCore`
2. **HTTP**：xxl-job GLUE/HTTP → `POST /execute`

Hub 调度 UI 可只读或关闭；配置仍在 xxl-job Admin。

## 8. Admin 停机行为

| 能力 | Admin 停 |
|------|----------|
| 已同步的链 Cron | ✅ Executor 继续 |
| 链执行 / 热加载 | ✅ |
| 改 Cron / 新建 | ❌ |
| 控制台 / 查 log | ❌ |
| 平台租户清理 | ❌ |

## 9. 迁移（自 v0.1 Admin 库 schedule）

1. 部署含 `zf_schedule` 的 Executor Flyway V2  
2. 将 Admin 库 `job_type=CHAIN` 的记录 **手工或脚本** 导入业务库（一次性）  
3. Admin Flyway 停用 `admin.schedule.scan` 平台任务  
4. 验证 Executor `LocalScheduleEngine` 日志与 `zf_schedule_log`

## 10. 参考

- xxl-job：分片、幂等、执行日志语义  
- PowerJob Worker 自治：触发在 Worker  
- Nacos：Hub 写配置，客户端本地副本执行  
- Quartz JDBC：任务定义在应用库  

**定位**：ZestFlow 调度中心 = **链 Cron 的配置与观测 Hub**，不是公司级 Cron 服务器。
