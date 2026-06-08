# 文档维护规范

> **版本** 0.1.0 · **更新** 2026-06-08 · [English](DOCUMENTATION_MAINTENANCE.en.md)

本文定义 ZestFlow 文档的版本控制、更新触发条件与质量检查清单，确保文档与代码长期一致。

---

## 1. 文档架构

采用 **Diátaxis** 四类：

| 类型 | 目录 | 更新频率 |
|------|------|---------|
| Tutorial | `GETTING_STARTED.md`, `MCP_SETUP.md` | 低（流程稳定时） |
| How-to | `guides/`, `DEPLOY.md` | 中 |
| Explanation | `ARCHITECTURE.md`, `adr/`, `PROJECT_SUMMARY.md` | 中 |
| Reference | `QUICK_REFERENCE.md`, `reference/` | **高**（随 API/配置变更） |

入口索引：[docs/README.md](README.md)

---

## 2. 版本标识

每篇面向用户的文档头部须包含：

```markdown
> **版本** 0.1.0 · **更新** YYYY-MM-DD · **类型** Tutorial|How-to|... · [English](同名.en.md)
```

- **版本**：与根 `pom.xml` `<version>` 对齐（发版时批量更新）
- **更新**：最后一次实质性修改日期
- **双语**：中文 `*.md` 与英文 `*.en.md` 成对维护；英文文首使用 `[简体中文](同名.md)` 回链

---

## 2.1 双语维护规则

| 规则 | 说明 |
|------|------|
| 命名 | 英文镜像与中文同目录，文件名加 `.en` 后缀，如 `API.md` ↔ `API.en.md` |
| 同步 | 修改中文用户文档时，**同一 PR** 更新对应 `.en.md`（或明确标注 follow-up Issue） |
| 互链 | 中文头 `[English](xxx.en.md)`；英文头 `[简体中文](xxx.md)` |
| 索引 | 新增文档时更新 `CATALOG.md`、`CATALOG.en.md`、`README.md`、`README.en.md` |
| 工具 | 批量补链：`scripts/docs/add-bilingual-headers.ps1` |
| 校验 | 发版或 CI 运行：`scripts/docs/verify-bilingual-docs.ps1` |
| 英文内链 | 批量修正：`scripts/docs/fix-en-internal-links.ps1` |
| 中文回链 | 修正英文页 `[简体中文]` 头：`scripts/docs/fix-zh-crosslinks.ps1` |

---

## 3. 触发更新的代码变更

| 代码变更 | 必须更新的文档 |
|---------|---------------|
| 配置项变更 | `reference/CONFIGURATION.md` + `.en.md`、`*Properties.java`、对应 `application.yml` |
| 新增/修改 Admin REST 端点 | `reference/API.md` + `.en.md`、运行 `export-openapi.ps1` |
| 新增注解 / 引擎 API | `reference/ANNOTATIONS.md`、`EXECUTION_ENGINE.md` 及英文镜像、`QUICK_REFERENCE.md` |
| 新增 SPI | `reference/SPI.md` + `.en.md`、`ARCHITECTURE.md` §12 |
| 用户高频问题 | `reference/FAQ.md` + `.en.md` |
| Flyway 新版本 | `FLYWAY_POLICY.md` + `.en.md`、迁移 README |
| 端口/部署流程变更 | `DEPLOY.md`、`GETTING_STARTED.md`、`GLOSSARY.md` 及英文镜像 |
| 发版 | `CHANGELOG.md` + `CHANGELOG.en.md`、README 版本徽章、各文档头部版本 |
| 架构决策 | 新增 `docs/adr/*.md` |

---

## 4. PR 文档检查清单

贡献者在 PR 中自检（Reviewer 复核）：

- [ ] 代码行为变更是否有对应文档更新？
- [ ] 配置项名称与 `*Properties.java` 一致？
- [ ] 示例命令在 Windows / Linux 均可理解（或注明平台）？
- [ ] 新术语是否加入 `reference/GLOSSARY.md`？
- [ ] 无重复大段内容（应交叉引用而非复制）？
- [ ] 中英文镜像是否同步更新？
- [ ] 运行 `scripts/docs/verify-bilingual-docs.ps1` 通过？
- [ ] 新文档是否已加入 CATALOG（中/英）与文档中心？
- [ ] 前端改动是否说明需 `pnpm build`？

---

## 5. 发版文档流程

1. 更新 `CHANGELOG.md`（Added / Changed / Fixed）
2. 批量更新文档头部 **版本** 号
3. 检查 `README.md` / `README.en.md` 特性列表与截图
4. 运行 [RELEASE_READINESS.md](RELEASE_READINESS.md) 清单
5. Tag 与 Maven 发版同步

---

## 6. 准确性验证

| 维度 | 验证方式 |
|------|---------|
| 配置默认值 | 对照 `application.yml` + `@ConfigurationProperties` |
| 端口 | 对照 `ExecutorProperties`、`CollectorRegistryProperties` |
| 注解 | 对照 `zestflow-executor` annotation 包 |
| API 路径 | 对照 `*Controller.java` |
| 模块列表 | 对照根 `pom.xml` `<modules>` |

建议每个 minor 版本做一次文档审计（可复用本任务检查清单）。

---

## 7. 已知待完善项

| 项 | 状态 | 说明 |
|----|------|------|
| OpenAPI/Swagger 导出 | ✅ | `springdoc` + `scripts/docs/export-openapi.ps1` + `docs/openapi/` |
| 英文文档 parity | ✅ | 35/35 用户文档 + CHANGELOG/CONTRIBUTING 英文镜像 |
| 视频教程 | 无 | 可选社区贡献 |

---

## 8. 文档质量评分标准（10 分制）

| 分数 | 标准 |
|------|------|
| 9-10 | 全链路覆盖；与代码零冲突；Diátaxis 清晰；新人 30 分钟可跑通 |
| 7-8 | 核心路径完整；少量过时配置或缺失 How-to |
| 5-6 | README 可用；缺 Reference 或部署细节 |
| <5 | 文档分散、与代码明显不一致 |

**当前目标：9+**（2026-06-08 文档重构后）

---

## 相关链接

- [docs/README.md](README.md) — 文档中心
- [CONTRIBUTING.md](../CONTRIBUTING.md) — 贡献流程
