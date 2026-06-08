# 文档完整清单

> **版本** 0.1.0 · **更新** 2026-06-08 · [← 文档中心](README.md)

本文档列出 `docs/` 下**全部** Markdown 文件及其分类、受众与维护状态。

---

## 统计

| 类别 | 数量 | 说明 |
|------|------|------|
| 用户文档（Tutorial / How-to / Reference） | 12 | 新人上手与日常开发 |
| 架构与原理（Explanation） | 6 | 深入理解 |
| AI 专项 | 8 | Copilot / MCP / IDE / 学习 / 验收 |
| 架构决策 ADR | 2 | 不可变决策记录 |
| 发布与交接 | 4 | 发版、就绪、跨机续跑 |
| 测试与验收 | 5 | 黑盒报告 + acceptance |
| 元文档 | 3 | 审计、维护、本清单 |
| **合计** | **30** | 不含根目录 README / CLAUDE.md |

---

## 一、用户文档

| 文件 | 类型 | 受众 | 状态 |
|------|------|------|------|
| [GETTING_STARTED.md](GETTING_STARTED.md) | Tutorial | 新用户 | ✅ 2026-06-08 新建 |
| [MCP_SETUP.md](MCP_SETUP.md) | Tutorial | IDE 开发者 | ✅ 已纳入索引 |
| [AI_IDE_SETUP.md](AI_IDE_SETUP.md) | Tutorial | 全 IDE 接入对照 | ✅ 2026-06-08 |
| [guides/COMPONENT_DEVELOPMENT.md](guides/COMPONENT_DEVELOPMENT.md) | How-to | 业务开发者 | ✅ 2026-06-08 新建 |
| [guides/CHAIN_ORCHESTRATION.md](guides/CHAIN_ORCHESTRATION.md) | How-to | 编排管理员 | ✅ 2026-06-08 新建 |
| [DEPLOY.md](DEPLOY.md) | How-to | 运维 | ✅ 已纳入索引 |
| [FLYWAY_POLICY.md](FLYWAY_POLICY.md) | How-to | DBA / 后端 | ✅ 已纳入索引 |
| [QUICK_REFERENCE.md](QUICK_REFERENCE.md) | Reference | 开发者 | ✅ 已纳入索引 |
| [reference/CONFIGURATION.md](reference/CONFIGURATION.md) | Reference | 开发者 / 运维 | ✅ 2026-06-08 新建 |
| [reference/GLOSSARY.md](reference/GLOSSARY.md) | Reference | 全员 | ✅ 2026-06-08 新建 |
| [../CHANGELOG.md](../CHANGELOG.md) | Reference | 全员 | ✅ 发版同步 |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | How-to | 贡献者 | ✅ 2026-06-08 新建 |
| [../README.md](../README.md) | 入口 | 全员 | ✅ 已更新导航 |

---

## 二、架构与原理

| 文件 | 类型 | 说明 | 状态 |
|------|------|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Explanation | C4 + 模块 + API 矩阵（~1800 行） | ✅ 2026-06-08 校正版本号 |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | Explanation | 引擎与 28 种元件类型概要 | ✅ 2026-06-08 校正技术栈 |
| [adr/SCHEDULING.md](adr/SCHEDULING.md) | ADR | Executor 自治 Cron | ✅ 已纳入索引 |
| [adr/SCHEDULING_SPI_XXLJOB.md](adr/SCHEDULING_SPI_XXLJOB.md) | ADR | xxl-job SPI 集成 | ✅ 已纳入索引 |

---

## 三、AI 专项文档

| 文件 | 类型 | 说明 | 状态 |
|------|------|------|------|
| [AI_COPILOT.md](AI_COPILOT.md) | Explanation + Reference | Admin 编排 Copilot 完整方案（~1000 行） | ✅ 已实现，内容完整 |
| [AI_COPILOT_OPS.md](AI_COPILOT_OPS.md) | How-to | AI 运维、RAG、预设配置 | ✅ 已实现 |
| [AI_DEV_COPILOT_FINAL_SOLUTION.md](AI_DEV_COPILOT_FINAL_SOLUTION.md) | ADR / 方案 | Dev MCP 最终架构决策 | ✅ Phase 1～3 已完成 |
| [AI_DEV_COPILOT_ACADEMIC_SUMMARY.md](AI_DEV_COPILOT_ACADEMIC_SUMMARY.md) | Explanation | 学术型背景与论辩过程 | ✅ 理论参考 |
| [AI_CHAIN_LEARNING.md](AI_CHAIN_LEARNING.md) | Explanation | Chain-first 学习与 RAG 分层 | ✅ P1～P3 |
| [AI_COPILOT_ACCEPTANCE.md](AI_COPILOT_ACCEPTANCE.md) | 验收 | AI 全流程生产验收规范 | ✅ 含自动化脚本 |
| [HANDOFF-AI-EXECUTOR.md](HANDOFF-AI-EXECUTOR.md) | 交接 | 跨机续跑 AI/Executor 知识库 | ✅ 维护者用 |

**AI 阅读路径：** `AI_COPILOT.md` → `MCP_SETUP.md` → `AI_CHAIN_LEARNING.md` → 验收文档

---

## 四、发布、交接与元文档

| 文件 | 说明 | 状态 |
|------|------|------|
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | 开源发布三层验收门禁 | ✅ |
| [PUBLISH_HANDOFF.md](PUBLISH_HANDOFF.md) | Maven Central 换机发版交接 | ✅ |
| [AUDIT_REPORT.md](AUDIT_REPORT.md) | 代码审计 + 文档质量评估 | ✅ 2026-06-08 |
| [DOCUMENTATION_MAINTENANCE.md](DOCUMENTATION_MAINTENANCE.md) | 文档版本控制与 PR 清单 | ✅ 2026-06-08 |
| [CATALOG.md](CATALOG.md) | 本清单 | ✅ 2026-06-08 |

---

## 五、测试与验收

| 文件 | 说明 | 自动化入口 |
|------|------|-----------|
| [FULL_E2E_TEST_REPORT.md](FULL_E2E_TEST_REPORT.md) | 全流程 E2E 报告模板 | `scripts/blackbox/run-full-e2e.ps1` |
| [BLACKBOX_TEST_REPORT.md](BLACKBOX_TEST_REPORT.md) | 黑盒冒烟 + 压测报告模板 | `scripts/blackbox/run-blackbox.ps1` |
| [acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md) | Executor AI v2 验收 | 手动 + 单元测试 |
| [acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md) | 调度/SLA/注册验收 | `run-scheduling-registry-sla-e2e.ps1` |
| [AI_COPILOT_ACCEPTANCE.md](AI_COPILOT_ACCEPTANCE.md) | AI Copilot 全流程验收 | `run-ai-copilot-acceptance.ps1` |

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
| OpenAPI REST 参考 | ARCHITECTURE §8 | 可从 Controller 自动生成 |
| 英文子文档 | README.en.md | 按需翻译 GETTING_STARTED |
| 多租户运维专篇 | DEPLOY + RELEASE_READINESS | 可合并为一篇 How-to |

---

## 维护说明

- 新增 `docs/**/*.md` 时必须更新**本清单**与 [README.md](README.md) 对应章节
- 专项文档内容已完整，本轮工作重点是**纳入文档中心 + 统一导航**，而非重写已有高质量长文
