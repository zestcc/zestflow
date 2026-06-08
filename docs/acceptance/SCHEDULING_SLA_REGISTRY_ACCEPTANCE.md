# 调度 / SLA / 注册 / xxl-job 生产级验收用例

> **版本** 0.2 · **更新** 2026-06-08 · **类型** 验收 · [← 文档中心](../README.md)  
> **范围**：嵌入式调度、SLA 下沉 Collector、事件驱动离线检测、xxl-job ExternalScheduleDriver

## 1. 验收原则

| 原则 | 说明 |
|------|------|
| **可重复** | 每条用例有前置条件、步骤、期望、自动化脚本 ID |
| **分层** | L1 单元 → L2 模块集成 → L3 黑盒 E2E → L4 压测门禁 |
| **生产等价** | 黑盒默认 `fullGreen` profile，失败 exit 1 |

## 2. 嵌入式链 Cron（EmbeddedScheduleDriver）

| ID | 场景 | 步骤 | 期望 | 自动化 |
|----|------|------|------|--------|
| SCH-001 | 业务库建表 | Flyway V2 执行 | `zf_schedule` / `zf_schedule_log` 存在 | `mvn test -pl zestflow-executor` |
| SCH-002 | 本地 Cron 触发 | 启用 schedule，插入 status=1 任务 | `zf_schedule_log` 有 cron 记录 | 黑盒 `run-scheduling-registry-sla-e2e.ps1` |
| SCH-003 | 分片过滤 | shard_total=2，两实例 shard-index 0/1 | 各实例仅触发归属任务 | `ScheduleShardSupportTest` |
| SCH-004 | Admin 代理 CRUD | POST/GET `/api/schedules` | 读写业务库 | `ScheduleServiceImplTest` |
| SCH-005 | Admin 停机 | 停 Admin，保留 Executor | Cron 仍触发 | 黑盒 SCH-005 段 |
| SCH-006 | 幂等 | 同一 fireEpoch 重复扫描 | 不重复执行 | `EmbeddedScheduleDriverTest` |

## 3. SLA 告警（Collector 扫描 + Admin 发信）

| ID | 场景 | 步骤 | 期望 | 自动化 |
|----|------|------|------|--------|
| SLA-001 | 扫描下沉 | Collector 定时 scan | Admin `admin.alert.execution-sla` status=0 | Flyway V5 |
| SLA-002 | 指标本地聚合 | Collector `queryStats` | 不上传原始事件 | `CollectorSlaAlertService` + 黑盒 |
| SLA-003 | 配置/冷却/邮件 | POST internal/process-metrics | 写 `alert_history`、冷却生效 | `AlertOrchestrationServiceTest` |
| SLA-004 | 链调度失败统计 | Executor fail-count API | 含 `zf_schedule_log` 失败 | `ScheduleChainProxyService.countFailures` |
| SLA-005 | 手动扫描 | POST `/api/alerts/scan` | 触发 Collector `/collector/alerts/scan` | 黑盒 |
| SLA-006 | 五类规则 | 低成功率/高失败/P95/无在线/调度失败 | 规则命中与 v0.1 一致 | `SlaAlertEvaluatorTest` |

## 4. 注册 / 离线（事件驱动）

| ID | 场景 | 步骤 | 期望 | 自动化 |
|----|------|------|------|--------|
| REG-001 | 心跳同步 DB | Executor 心跳 | `last_heartbeat` 立即更新，无 flush 任务 | 黑盒 REG-001 |
| REG-002 | 停用 heartbeat-flush | 查 platform job | `admin.registry.heartbeat-flush` status=0 | Flyway V5 |
| REG-003 | 过期离线 | 停止心跳 >90s | status ONLINE→ABNORMAL | `RegistryLifecycleServiceTest` + 黑盒 |
| REG-004 | 恢复在线 | 恢复心跳 | ABNORMAL→ONLINE | `OfflineMonitorTest` / 黑盒 |
| REG-005 | 停用轮询 offline-check | 查 platform job | status=0，无 30s 扫库 | Flyway V5 |
| REG-006 | 异常清理 | 24h 未恢复 ABNORMAL | 物理删除 | `OfflineMonitorTest` |

## 5. xxl-job ExternalScheduleDriver

| ID | 场景 | 步骤 | 期望 | 自动化 |
|----|------|------|------|--------|
| XXL-001 | driver 切换 | `driver=external` | 无 Embedded 15s 扫描 | `ExecutorAutoConfig` 条件装配 |
| XXL-002 | Handler 执行 | `@XxlJob zestflowChainJob` + chainCode 参数 | `ChainExecuteFacade.executeCore` | `XxlJobChainJobHandlerTest` |
| XXL-003 | 幂等键 | 触发带 jobId/triggerTime | idempotencyKey 含 xxl 前缀 | 单元断言 |
| XXL-004 | noop 模式 | `driver=noop` | 不扫库、不启 xxl | Bean 条件测试 |
| XXL-005 | 与 Embedded 互斥 | 同进程仅一 Driver 启动 | ApplicationRunner 日志 driverId | 集成日志 |

## 6. 黑盒 / 压测门禁

| 脚本 | 用途 |
|------|------|
| `scripts/blackbox/run-scheduling-registry-sla-e2e.ps1` | SCH/SLA/REG 专项 E2E |
| `scripts/blackbox/run-enterprise-gate.ps1` | 发布门禁（含 mvn test + full E2E） |
| `scripts/blackbox/run-perf-gate.ps1` | 调度扫描 + 心跳路径 P99 门禁 |

### 6.1 黑盒用例清单（run-scheduling-registry-sla-e2e.ps1）

1. **BB-SCH-01** Admin `/api/schedules` 列表可达  
2. **BB-SLA-01** Collector `/collector/alerts/scan` 返回 summary  
3. **BB-SLA-02** Admin `/api/alerts/scan` JWT 手动触发  
4. **BB-REG-01** 注册心跳后 DB `last_heartbeat` 更新（对比两次）  
5. **BB-REG-02** platform job 无 heartbeat-flush / offline-check 启用  
6. **BB-INT-01** Admin internal `/internal/alerts/scopes` registry-token  

### 6.2 压测场景（run-perf-gate 扩展）

| 场景 | 并发 | 指标 |
|------|------|------|
| Executor 心跳 30s 风暴 | 50 线程 × 5min | Admin P99 < 200ms |
| Embedded 扫描 | 100 条 schedule | 单轮扫描 < 2s |
| Collector SLA scan | 10 租户 × 5 模块 | 单次 < 5s |

## 7. 执行命令

```powershell
# L1-L2 单元/模块
mvn test -pl zestflow-common,zestflow-executor,zestflow-admin,zestflow-collector/collector-jdbc -am

# L3 专项黑盒（需 Admin:8080 + Executor:20550 + Collector:20650）
powershell -File scripts/blackbox/run-scheduling-registry-sla-e2e.ps1

# L3 全量门禁
powershell -File scripts/blackbox/run-enterprise-gate.ps1

# L4 压测（可选）
powershell -File scripts/blackbox/run-perf-gate.ps1
```

## 8. 签收标准

- [ ] 上表 L1 用例全部绿色  
- [ ] 黑盒 BB-* 在 fullGreen 下 0 失败  
- [ ] Flyway V5 在空库/升级库均可执行  
- [ ] `docs/adr/SCHEDULING.md` 与本文档一致  
- [ ] 生产配置：`zestflow.admin.registry-token`、`zestflow.mail.enabled=true`（若需邮件）
