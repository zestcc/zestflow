# 文档完整清单

> **版本** 0.1.0 · **更新** 2026-06-08 · [← 文档中心](README.md) · [English](CATALOG.en.md)

本文档列出 `docs/` 下**全部** Markdown 文件及其分类、受众与维护状态。**35 篇用户文档均已提供英文镜像**（`*.en.md`），头部含 `[English]` / `[简体中文]` 互链。

---

## 统计

| 类别 | 数量 | 说明 |
|------|------|------|
| 用户文档（Tutorial / How-to / Reference） | 17 | 含 API/注解/引擎/SPI/FAQ 专篇 |
| 架构与原理（Explanation） | 6 | 深入理解 |
| AI 专项 | 8 | Copilot / MCP / IDE / 学习 / 验收 |
| 架构决策 ADR | 2 | 不可变决策记录 |
| 发布与交接 | 4 | 发版、就绪、跨机续跑 |
| 测试与验收 | 5 | 黑盒报告 + acceptance |
| 元文档 | 3 | 审计、维护、本清单 |
| **合计** | **35** | 不含根目录 README / CLAUDE.md；**英文镜像 35/35** |

---

## 一、用户文档

| 文件 | 类型 | 受众 | EN | 状态 |
|------|------|------|----|------|
| [GETTING_STARTED.md](GETTING_STARTED.md) | Tutorial | 新用户 | [EN](GETTING_STARTED.en.md) | ✅ 2026-06-08 |
| [MCP_SETUP.md](MCP_SETUP.md) | Tutorial | IDE 开发者 | [EN](MCP_SETUP.en.md) | ✅ |
| [AI_IDE_SETUP.md](AI_IDE_SETUP.md) | Tutorial | 全 IDE 接入 | [EN](AI_IDE_SETUP.en.md) | ✅ 2026-06-08 |
| [guides/COMPONENT_DEVELOPMENT.md](guides/COMPONENT_DEVELOPMENT.md) | How-to | 业务开发者 | [EN](guides/COMPONENT_DEVELOPMENT.en.md) | ✅ 2026-06-08 |
| [guides/CHAIN_ORCHESTRATION.md](guides/CHAIN_ORCHESTRATION.md) | How-to | 编排管理员 | [EN](guides/CHAIN_ORCHESTRATION.en.md) | ✅ 2026-06-08 |
| [DEPLOY.md](DEPLOY.md) | How-to | 运维 | [EN](DEPLOY.en.md) | ✅ |
| [FLYWAY_POLICY.md](FLYWAY_POLICY.md) | How-to | DBA / 后端 | [EN](FLYWAY_POLICY.en.md) | ✅ |
| [QUICK_REFERENCE.md](QUICK_REFERENCE.md) | Reference | 开发者 | [EN](QUICK_REFERENCE.en.md) | ✅ |
| [reference/CONFIGURATION.md](reference/CONFIGURATION.md) | Reference | 配置项 | [EN](reference/CONFIGURATION.en.md) | ✅ 2026-06-08 |
| [reference/API.md](reference/API.md) | Reference | REST/Netty | [EN](reference/API.en.md) | ✅ 2026-06-08 |
| [reference/ANNOTATIONS.md](reference/ANNOTATIONS.md) | Reference | 注解 | [EN](reference/ANNOTATIONS.en.md) | ✅ 2026-06-08 |
| [reference/EXECUTION_ENGINE.md](reference/EXECUTION_ENGINE.md) | Reference | 引擎 API | [EN](reference/EXECUTION_ENGINE.en.md) | ✅ 2026-06-08 |
| [reference/SPI.md](reference/SPI.md) | Reference | 扩展点 | [EN](reference/SPI.en.md) | ✅ 2026-06-08 |
| [reference/OPENAPI.md](reference/OPENAPI.md) | Reference | OpenAPI 3 | [EN](reference/OPENAPI.en.md) | ✅ 2026-06-08 |
| [reference/FAQ.md](reference/FAQ.md) | Reference | 常见问题 | [EN](reference/FAQ.en.md) | ✅ 2026-06-08 |
| [openapi/README.md](openapi/README.md) | Reference | 规范快照 | [EN](openapi/README.en.md) | ✅ 2026-06-08 |
| [reference/GLOSSARY.md](reference/GLOSSARY.md) | Reference | 术语 | [EN](reference/GLOSSARY.en.md) | ✅ 2026-06-08 |
| [../CHANGELOG.md](../CHANGELOG.md) | Reference | 全员 | [EN](../CHANGELOG.en.md) | ✅ |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | How-to | 贡献者 | [EN](../CONTRIBUTING.en.md) | ✅ 2026-06-08 |
| [../README.md](../README.md) | 入口 | 全员 | [EN](../README.en.md) | ✅ |

---

## 二、架构与原理

| 文件 | 类型 | 说明 | EN | 状态 |
|------|------|------|----|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Explanation | C4 + 模块 + API 矩阵（~1800 行） | [EN](ARCHITECTURE.en.md) | ✅ 2026-06-08 |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | Explanation | 引擎与 28 种元件类型概要 | [EN](PROJECT_SUMMARY.en.md) | ✅ 2026-06-08 |
| [adr/SCHEDULING.md](adr/SCHEDULING.md) | ADR | Executor 自治 Cron | [EN](adr/SCHEDULING.en.md) | ✅ |
| [adr/SCHEDULING_SPI_XXLJOB.md](adr/SCHEDULING_SPI_XXLJOB.md) | ADR | xxl-job SPI 集成 | [EN](adr/SCHEDULING_SPI_XXLJOB.en.md) | ✅ |

---

## 三、AI 专项文档

| 文件 | 类型 | 说明 | EN | 状态 |
|------|------|------|----|------|
| [AI_COPILOT.md](AI_COPILOT.md) | Explanation + Reference | Admin 编排 Copilot（~1000 行） | [EN](AI_COPILOT.en.md) | ✅ |
| [AI_COPILOT_OPS.md](AI_COPILOT_OPS.md) | How-to | AI 运维、RAG、预设 | [EN](AI_COPILOT_OPS.en.md) | ✅ |
| [MCP_SETUP.md](MCP_SETUP.md) | Tutorial | Dev MCP 安装 | [EN](MCP_SETUP.en.md) | ✅ |
| [AI_IDE_SETUP.md](AI_IDE_SETUP.md) | Tutorial | 全 IDE MCP 对照 | [EN](AI_IDE_SETUP.en.md) | ✅ |
| [AI_DEV_COPILOT_FINAL_SOLUTION.md](AI_DEV_COPILOT_FINAL_SOLUTION.md) | ADR / 方案 | Dev MCP 最终架构 | [EN](AI_DEV_COPILOT_FINAL_SOLUTION.en.md) | ✅ |
| [AI_DEV_COPILOT_ACADEMIC_SUMMARY.md](AI_DEV_COPILOT_ACADEMIC_SUMMARY.md) | Explanation | 学术背景 | [EN](AI_DEV_COPILOT_ACADEMIC_SUMMARY.en.md) | ✅ |
| [AI_CHAIN_LEARNING.md](AI_CHAIN_LEARNING.md) | Explanation | Chain-first 学习与 RAG | [EN](AI_CHAIN_LEARNING.en.md) | ✅ |
| [AI_COPILOT_ACCEPTANCE.md](AI_COPILOT_ACCEPTANCE.md) | 验收 | AI 全流程生产验收 | [EN](AI_COPILOT_ACCEPTANCE.en.md) | ✅ |
| [HANDOFF-AI-EXECUTOR.md](HANDOFF-AI-EXECUTOR.md) | 交接 | 跨机续跑知识库 | [EN](HANDOFF-AI-EXECUTOR.en.md) | ✅ |

**AI 阅读路径：** `AI_COPILOT.md` → `MCP_SETUP.md` → `AI_CHAIN_LEARNING.md` → 验收文档

---

## 四、发布、交接与元文档

| 文件 | 说明 | EN | 状态 |
|------|------|----|------|
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | 开源发布三层验收门禁 | [EN](RELEASE_READINESS.en.md) | ✅ |
| [PUBLISH_HANDOFF.md](PUBLISH_HANDOFF.md) | Maven Central 换机发版 | [EN](PUBLISH_HANDOFF.en.md) | ✅ |
| [AUDIT_REPORT.md](AUDIT_REPORT.md) | 代码审计 + 文档质量 | [EN](AUDIT_REPORT.en.md) | ✅ 2026-06-08 |
| [DOCUMENTATION_MAINTENANCE.md](DOCUMENTATION_MAINTENANCE.md) | 文档维护规范 | [EN](DOCUMENTATION_MAINTENANCE.en.md) | ✅ |
| [CATALOG.md](CATALOG.md) | 本清单 | [EN](CATALOG.en.md) | ✅ |

---

## 五、测试与验收

| 文件 | 说明 | EN | 自动化入口 |
|------|------|----|-----------|
| [FULL_E2E_TEST_REPORT.md](FULL_E2E_TEST_REPORT.md) | 全流程 E2E 报告 | [EN](FULL_E2E_TEST_REPORT.en.md) | `run-full-e2e.ps1` |
| [BLACKBOX_TEST_REPORT.md](BLACKBOX_TEST_REPORT.md) | 黑盒冒烟 + 压测 | [EN](BLACKBOX_TEST_REPORT.en.md) | `run-blackbox.ps1` |
| [acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md) | Executor AI v2 | [EN](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.en.md) | 手动 + 单元测试 |
| [acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md) | 调度/SLA/注册 | [EN](acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.en.md) | `run-scheduling-registry-sla-e2e.ps1` |
| [AI_COPILOT_ACCEPTANCE.md](AI_COPILOT_ACCEPTANCE.md) | AI Copilot 全流程 | [EN](AI_COPILOT_ACCEPTANCE.en.md) | `run-ai-copilot-acceptance.ps1` |

---

## 六、仓库外相关文档

| 路径 | 说明 |
|------|------|
| [../CLAUDE.md](../CLAUDE.md) | AI 协作开发规范（非用户文档） |
| [../zestflow-admin/src/main/resources/db/migration/README.md](../zestflow-admin/src/main/resources/db/migration/README.md) | Admin Flyway 迁移说明 |
| [../scripts/blackbox/README.md](../scripts/blackbox/README.md) | 黑盒脚本用法 |
| [../scripts/dev/mcp/README.md](../scripts/dev/mcp/README.md) | MCP 安装脚本说明 |

---

## 七、尚未单独成篇的主题（可选后续）

| 主题 | 当前覆盖位置 | 建议 |
|------|-------------|------|
| OpenAPI REST 参考 | ✅ `springdoc` + `docs/openapi/admin-api.json` | 发版前运行 `export-openapi.ps1` |
| 双语文档 | ✅ 35/35 用户文档 + CHANGELOG/CONTRIBUTING | 修改中文时同步 `*.en.md` |
| 多租户运维专篇 | DEPLOY + RELEASE_READINESS | 可合并为一篇 How-to |

---

## 维护说明

- 新增 `docs/**/*.md` 时必须更新**本清单**与 [README.md](README.md) 对应章节
- 专项文档内容已完整，本轮工作重点是**纳入文档中心 + 统一导航**，而非重写已有高质量长文
