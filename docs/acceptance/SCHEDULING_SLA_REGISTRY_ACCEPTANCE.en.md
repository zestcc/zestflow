# Scheduling / SLA / Registry / xxl-job Production Acceptance Cases

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md) · **Type** Acceptance · [← Documentation hub](../README.en.md)  
> **Scope:** Embedded scheduling, SLA moved to Collector, event-driven offline detection, xxl-job ExternalScheduleDriver

## 1. Acceptance Principles

| Principle | Description |
|-----------|-------------|
| **Repeatable** | Each case has prerequisites, steps, expectations, automation script ID |
| **Layered** | L1 unit → L2 module integration → L3 black-box E2E → L4 load gate |
| **Production equivalent** | Black-box defaults to `fullGreen` profile; failure exit 1 |

## 2. Embedded Chain Cron (EmbeddedScheduleDriver)

| ID | Scenario | Steps | Expected | Automation |
|----|----------|-------|----------|------------|
| SCH-001 | Business DB tables | Flyway V2 runs | `zf_schedule` / `zf_schedule_log` exist | `mvn test -pl zestflow-executor` |
| SCH-002 | Local Cron trigger | Enable schedule, insert status=1 task | `zf_schedule_log` has cron record | Black-box `run-scheduling-registry-sla-e2e.ps1` |
| SCH-003 | Shard filter | shard_total=2, two instances shard-index 0/1 | Each instance triggers only owned tasks | `ScheduleShardSupportTest` |
| SCH-004 | Admin proxy CRUD | POST/GET `/api/schedules` | Read/write business DB | `ScheduleServiceImplTest` |
| SCH-005 | Admin down | Stop Admin, keep Executor | Cron still triggers | Black-box SCH-005 segment |
| SCH-006 | Idempotency | Same fireEpoch repeated scan | No duplicate execution | `EmbeddedScheduleDriverTest` |

## 3. SLA Alerts (Collector Scan + Admin Mail)

| ID | Scenario | Steps | Expected | Automation |
|----|----------|-------|----------|------------|
| SLA-001 | Scan moved | Collector periodic scan | Admin `admin.alert.execution-sla` status=0 | Flyway V5 |
| SLA-002 | Local metric aggregation | Collector `queryStats` | No raw event upload | `CollectorSlaAlertService` + black-box |
| SLA-003 | Config/cooldown/mail | POST internal/process-metrics | Writes `alert_history`, cooldown effective | `AlertOrchestrationServiceTest` |
| SLA-004 | Chain schedule failure stats | Executor fail-count API | Includes `zf_schedule_log` failures | `ScheduleChainProxyService.countFailures` |
| SLA-005 | Manual scan | POST `/api/alerts/scan` | Triggers Collector `/collector/alerts/scan` | Black-box |
| SLA-006 | Five rule types | Low success rate/high failure/P95/no online/schedule failure | Rule hits consistent with v0.1 | `SlaAlertEvaluatorTest` |

## 4. Registry / Offline (Event-Driven)

| ID | Scenario | Steps | Expected | Automation |
|----|----------|-------|----------|------------|
| REG-001 | Heartbeat sync DB | Executor heartbeat | `last_heartbeat` updated immediately, no flush task | Black-box REG-001 |
| REG-002 | Disable heartbeat-flush | Query platform job | `admin.registry.heartbeat-flush` status=0 | Flyway V5 |
| REG-003 | Expiry offline | Stop heartbeat >90s | status ONLINE→ABNORMAL | `RegistryLifecycleServiceTest` + black-box |
| REG-004 | Recover online | Resume heartbeat | ABNORMAL→ONLINE | `OfflineMonitorTest` / black-box |
| REG-005 | Disable polling offline-check | Query platform job | status=0, no 30s DB scan | Flyway V5 |
| REG-006 | Abnormal cleanup | ABNORMAL unrecovered 24h | Physical delete | `OfflineMonitorTest` |

## 5. xxl-job ExternalScheduleDriver

| ID | Scenario | Steps | Expected | Automation |
|----|----------|-------|----------|------------|
| XXL-001 | Driver switch | `driver=external` | No Embedded 15s scan | `ExecutorAutoConfig` conditional assembly |
| XXL-002 | Handler execution | `@XxlJob zestflowChainJob` + chainCode param | `ChainExecuteFacade.executeCore` | `XxlJobChainJobHandlerTest` |
| XXL-003 | Idempotency key | Trigger with jobId/triggerTime | idempotencyKey contains xxl prefix | Unit assertion |
| XXL-004 | noop mode | `driver=noop` | No DB scan, no xxl | Bean conditional test |
| XXL-005 | Mutually exclusive with Embedded | Same process only one Driver starts | ApplicationRunner log driverId | Integration log |

## 6. Black-Box / Load Gates

| Script | Purpose |
|--------|---------|
| `scripts/blackbox/run-scheduling-registry-sla-e2e.ps1` | SCH/SLA/REG specialty E2E |
| `scripts/blackbox/run-enterprise-gate.ps1` | Release gate (includes mvn test + full E2E) |
| `scripts/blackbox/run-perf-gate.ps1` | Schedule scan + heartbeat path P99 gate |

### 6.1 Black-Box Case List (run-scheduling-registry-sla-e2e.ps1)

1. **BB-SCH-01** Admin `/api/schedules` list reachable  
2. **BB-SLA-01** Collector `/collector/alerts/scan` returns summary  
3. **BB-SLA-02** Admin `/api/alerts/scan` JWT manual trigger  
4. **BB-REG-01** After register heartbeat DB `last_heartbeat` updated (compare twice)  
5. **BB-REG-02** Platform job has no heartbeat-flush / offline-check enabled  
6. **BB-INT-01** Admin internal `/internal/alerts/scopes` registry-token  

### 6.2 Load Scenarios (run-perf-gate extension)

| Scenario | Concurrency | Metric |
|----------|-------------|--------|
| Executor heartbeat 30s storm | 50 threads × 5min | Admin P99 < 200ms |
| Embedded scan | 100 schedules | Single round scan < 2s |
| Collector SLA scan | 10 tenants × 5 modules | Single run < 5s |

## 7. Execution Commands

```powershell
# L1-L2 unit/module
mvn test -pl zestflow-common,zestflow-executor,zestflow-admin,zestflow-collector/collector-jdbc -am

# L3 specialty black-box (requires Admin:8080 + Executor:20550 + Collector:20650)
powershell -File scripts/blackbox/run-scheduling-registry-sla-e2e.ps1

# L3 full gate
powershell -File scripts/blackbox/run-enterprise-gate.ps1

# L4 load (optional)
powershell -File scripts/blackbox/run-perf-gate.ps1
```

## 8. Sign-Off Criteria

- [ ] All L1 cases in table above green  
- [ ] Black-box BB-* zero failures under fullGreen  
- [ ] Flyway V5 runs on empty and upgrade databases  
- [ ] `docs/adr/SCHEDULING.en.md` consistent with this document  
- [ ] Production config: `zestflow.admin.registry-token`, `zestflow.mail.enabled=true` (if mail needed)
