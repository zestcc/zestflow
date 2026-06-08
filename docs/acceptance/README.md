# 验收测试文档索引

> **版本** 0.1.0 · **更新** 2026-06-08 · **语言** 简体中文 · [English](README.en.md) · [← 文档中心](../README.md)

本目录包含 ZestFlow 各子系统的生产级验收标准与测试矩阵。每篇文档定义可重复场景、期望结果，并在可用时关联自动化测试脚本。

---

## 文档列表

### [AI_EXECUTOR_V2_ACCEPTANCE.md](AI_EXECUTOR_V2_ACCEPTANCE.md)

**Executor AI v2** 生产验收清单（2026-06-08），涵盖基于 LLM 的链建议（含 pattern 回落）、Hybrid RAG（keyword / TF-IDF / embedding 重排）、设计画布 CONDITION 节点布局（BPMN 风格 True 左 / False 右）。含 `zestflow-executor` 与 `zestflow-admin-ui` 单元测试命令。

**英文版**：[AI_EXECUTOR_V2_ACCEPTANCE.en.md](AI_EXECUTOR_V2_ACCEPTANCE.en.md)

### [SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md)

**调度、SLA 告警、注册生命周期、xxl-job 集成** 生产验收矩阵。覆盖内嵌链 Cron（`EmbeddedScheduleDriver`）、SLA 扫描下沉 Collector、事件驱动离线检测（替代 Admin 轮询）、`ExternalScheduleDriver` 与 xxl-job Handler。含黑盒脚本 ID（`run-scheduling-registry-sla-e2e.ps1`、`run-enterprise-gate.ps1`、`run-perf-gate.ps1`）及分层测试原则（L1 单元 → L4 压测门禁）。

**英文版**：[SCHEDULING_SLA_REGISTRY_ACCEPTANCE.en.md](SCHEDULING_SLA_REGISTRY_ACCEPTANCE.en.md)

### [AI Copilot 全流程验收](../AI_COPILOT_ACCEPTANCE.md)

Admin Orchestration Copilot + Dev MCP 双 Copilot 生产验收规范，含自动化脚本对照与 UI 走查清单。

**英文版**：[AI_COPILOT_ACCEPTANCE.en.md](../AI_COPILOT_ACCEPTANCE.en.md)

---

## 运行验收测试

| 领域 | 命令 |
|------|------|
| AI Copilot（Admin） | `powershell -File scripts/blackbox/run-ai-copilot-acceptance.ps1 -UseMockLlm` |
| 调度 / SLA / 注册 | `powershell -File scripts/blackbox/run-scheduling-registry-sla-e2e.ps1` |
| 企业发布门禁 | `powershell -File scripts/blackbox/run-enterprise-gate.ps1` |

各验收文档中有场景 ID、前置条件与期望结果说明。

---

## 相关文档

| 主题 | 文档 |
|------|------|
| 发布就绪清单 | [RELEASE_READINESS.md](../RELEASE_READINESS.md) |
| 全流程 E2E 报告 | [FULL_E2E_TEST_REPORT.md](../FULL_E2E_TEST_REPORT.md) |
| 黑盒测试报告 | [BLACKBOX_TEST_REPORT.md](../BLACKBOX_TEST_REPORT.md) |
