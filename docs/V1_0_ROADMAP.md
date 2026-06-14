# ZestFlow v1.0.0 路线图

> **状态**：进行中 · **目标 tag**：`v1.0.0` · [← 文档中心](README.md)

## 1.0 发布契约

对外承诺：**可生产试点的嵌入式编排引擎** — 可视化建链、全链路观测、Cron 调度、企业安全与多租户可验证，**公共 API 自 1.0 起稳定**。

## P0 — 发布阻塞（必须完成）

| # | 项 | 验收 |
|---|-----|------|
| 1 | 版本统一 `1.0.0-SNAPSHOT` → `1.0.0` | 全模块 POM + README + CHANGELOG |
| 2 | [MIGRATION_0.x_to_1.0.md](MIGRATION_0.x_to_1.0.md) | 配置/Breaking/DB |
| 3 | **StrictV1 门禁** | `run-v1-acceptance.ps1` Exit 0 |
| 4 | Maven Central 1.0.0 | starter 可依赖 |
| 5 | 前端 static 进 jar | CI / 发版脚本 `npm run build` |
| 6 | API 稳定声明 | CHANGELOG + ARCHITECTURE §协议 |

## P1 — 1.0 产品完整（本里程碑包含）

| # | 项 | 验收 |
|---|-----|------|
| 7 | WebSocket 日志流 GA | 默认开启 + 前端 auto + E2E |
| 8 | Fallback 策略 | constant / propagate / default + 单测 |
| 9 | 多租户 How-to | [guides/MULTI_TENANT.md](guides/MULTI_TENANT.md) + enterprise E2E |
| 10 | Admin cluster 构建 | `mvn -Pcluster test` 进 StrictV1 |
| 11 | 全 profile E2E | `run-all-profiles-e2e.ps1` 纳入 v1 门禁 |

## 明确不做（1.0）

- BPMN / 人工审批（Flowable 赛道）
- Temporal 式跨服务 durable workflow
- gRPC 传输层（1.1+）

## StrictV1 门禁命令

```powershell
# 需 Admin :8080 + Demo :20550 + Collector :20650（local profile）
.\scripts\blackbox\run-v1-acceptance.ps1
```

等价于：全量 `mvn test` + `npm run build` + cluster 构建测试 + 全 profile E2E + 严格 production-acceptance（perf + offline + enterprise/security 由 profile 编排覆盖）。
