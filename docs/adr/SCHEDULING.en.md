# ADR: ZestFlow Scheduling Architecture (Cron / Sharding / SPI)

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](SCHEDULING.md) · **Status** Accepted · **Version** 0.2 evolution · [← Documentation hub](../README.en.md)

## 1. Background

ZestFlow adopts **Hub control plane + business data plane separation** (three databases: Admin / business / log).  
v0.1 business chain Cron was scanned by Admin every 15s and HTTP-called Executor (benchmark xxl-job Admin), causing:

- Admin downtime → platform Cron stops (conflicts with "Admin does not affect business")
- Config in Admin DB; Executor cannot self-govern
- Admin active fan-out (reconciliation, SLA) grows heavy with node count

## 2. Decision Summary

| Layer | Responsibility |
|-------|----------------|
| **Admin** | Platform-owned Cron (tenant cleanup, etc.); chain schedule **CRUD/query proxy**; **does not** scan business Cron |
| **Business DB** | `zf_schedule` / `zf_schedule_log` are **CHAIN schedule source of truth** |
| **Executor** | Default **EmbeddedScheduleDriver**: reads business DB, local Cron, sharding, in-process chain execution |
| **Collector** | Observation Cron sunk (SLA, etc., future iterations) |
| **SPI** | `ScheduleDriver` replaceable with xxl-job / HTTP external triggers |

**Out of scope:** Admin cluster HA as prerequisite for business Cron; embedded full xxl-job Admin.

## 3. Task Ownership

### 3.1 Admin Local Cron (`module=admin`, `remote=0`)

- Trial tenant cleanup `admin.tenant.cleanup`
- Registry offline detection / cleanup (control plane)
- Chain sync cache eviction (Admin publish UI)
- Platform email alerts (reads Admin DB config)

### 3.2 Executor Local (`module=executor`, `remote=1`)

- Chain hot reload `executor.chain.reload`
- Registration heartbeat `executor.registry.heartbeat`
- **Embedded chain Cron** `executor.schedule.embedded` (reads `zf_schedule`)

### 3.3 Collector Local (`module=collector`, `remote=1`)

- Registration heartbeat
- **Execution SLA scan** `collector.alert.execution-sla` (reads chain_event; Admin sends mail/cooldown/history)

### 3.4 Removed / Event-Driven

- ~~`admin.schedule.scan`~~ — Business Cron no longer triggered by Admin
- ~~`admin.registry.heartbeat-flush`~~ — Heartbeat events sync DB `last_heartbeat`
- ~~`admin.registry.offline-check`~~ — Heartbeat expiry events mark ABNORMAL (`RegistryLifecycleService`)
- ~~`admin.alert.execution-sla`~~ — SLA scan moved to Collector

## 4. Data Model (Business DB)

### `zf_schedule`

| Field | Description |
|-------|-------------|
| `cron` | Cron expression |
| `chain_code` / `chain_name` | Target chain |
| `route_strategy` | `local` (default) / `round_robin` / `hash` — for manual trigger |
| `shard_total` | Total shards, default 1 |
| `shard_param` | Shard hash key, default `schedule_id` |
| `params` | JSON input params |
| `status` | 0 stop / 1 enable |
| `tenant_id` / `app_code` | Tenant and application |

### `zf_schedule_log`

Written after Executor trigger; Admin queries via HTTP proxy.

### Sharding

- **Static:** `shard_total` in `zf_schedule`
- **Local index:** `zestflow.executor.shard-index` / `shard-total` (yml/env)
- **Decision:** `hash(scheduleId) % shardTotal == shardIndex` → this instance owns the task

## 5. Trigger Flow (Default Embedded)

```text
LocalScheduleEngine (Executor, every 15s)
  → Read zf_schedule (status=1)
  → Shard filter
  → CronExpression check due
  → ScheduleTriggerService.executeInProcess(chainCode, params, idempotencyKey)
  → Write zf_schedule_log
```

**Idempotency key:** `schedule-{id}-cron-{fireEpochMs}` (consistent with v0.1)

**Manual trigger:** Admin `POST /schedules/{id}/trigger` → proxies Executor `POST /api/schedules/{id}/trigger`

## 6. Admin Proxy (Hub)

Same pattern as `/chains`:

```text
GET/POST/PUT/DELETE  /api/schedules  →  Executor Netty
GET                  /api/schedules/logs
POST                 /api/schedules/{id}/trigger
```

Admin DB `schedule` table **retains PLATFORM tasks only** (schedule center shows platform built-in tasks).

## 7. SPI: `ScheduleDriver`

```text
com.zestflow.common.spi.schedule.ScheduleDriver
  ├─ EmbeddedScheduleDriver   (default, spring.factories / AutoConfig)
  ├─ NoopScheduleDriver       (schedule.enabled=false)
  └─ (extension) XxlJobScheduleDriver / HttpCallbackScheduleDriver
```

Configuration:

```yaml
zestflow:
  executor:
    schedule:
      enabled: true
      driver: embedded   # embedded | noop | external (reserved)
      poll-interval-ms: 15000
    shard-index: 0
    shard-total: 1
```

### xxl-job Integration (Extension, Not Default)

1. **Handler inside Executor:** xxl-job callback → `ChainExecuteFacade.executeCore`
2. **HTTP:** xxl-job GLUE/HTTP → `POST /execute`

Hub schedule UI may be read-only or disabled; config remains in xxl-job Admin.

## 8. Admin Downtime Behavior

| Capability | Admin down |
|------------|------------|
| Synced chain Cron | ✅ Executor continues |
| Chain execution / hot reload | ✅ |
| Change Cron / create new | ❌ |
| Console / query log | ❌ |
| Platform tenant cleanup | ❌ |

## 9. Migration (From v0.1 Admin DB schedule)

1. Deploy Executor Flyway V2 with `zf_schedule`  
2. **Manually or via script** import Admin DB `job_type=CHAIN` records to business DB (one-time)  
3. Admin Flyway disables `admin.schedule.scan` platform task  
4. Verify Executor `LocalScheduleEngine` logs and `zf_schedule_log`

## 10. References

- xxl-job: sharding, idempotency, execution log semantics  
- PowerJob Worker autonomy: trigger on Worker  
- Nacos: Hub writes config, client local copy executes  
- Quartz JDBC: task definition in application DB  

**Positioning:** ZestFlow schedule center = **chain Cron configuration and observation Hub**, not company-wide Cron server.
