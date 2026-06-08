# 贡献指南

> **语言** 简体中文 · [English](CONTRIBUTING.en.md)

感谢你对 ZestFlow 的关注。本文说明如何参与代码与文档贡献。

---

## 行为准则

- 尊重讨论，对事不对人
- Issue 中提供可复现步骤或最小示例
- PR 保持单一主题，避免无关改动

---

## 开发环境

1. Fork 仓库（Gitee / GitHub）
2. JDK 17+、Maven 3.8+、MySQL 8+
3. 按 [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md) 启动 Admin + Demo

```bash
git clone <your-fork>
cd zestflow
mvn install -pl zestflow-demo -am -DskipTests
```

---

## 提交前检查

```bash
# 核心模块单元测试
mvn test -pl zestflow-admin,zestflow-executor,zestflow-common -am

# Demo 集成测试（可选，耗时较长）
mvn test -pl zestflow-demo -am

# 前端（若改动 admin-ui）
cd zestflow-admin-ui && pnpm test:unit && pnpm build
```

---

## 代码规范

项目规范详见根目录 [CLAUDE.md](CLAUDE.md)，要点：

| 领域 | 要求 |
|------|------|
| 包命名 | `com.zestflow.{模块}.{分层}` |
| 分层 | Controller → Service → Repository，禁止跨层 |
| 模块依赖 | 各端只依赖 `zestflow-common`，禁止 Admin ↔ Executor 直接依赖 |
| 数据库变更 | 仅 Flyway `db/migration/V{n}__*.sql`，禁止 Java DDL |
| 前端 UI 文本 | 必须 i18n，禁止硬编码中文/英文 |
| 编码生成 | 设计/链编码由 `CodeGenerator` 自动生成，禁止手填 |

---

## 文档贡献

1. 阅读 [docs/DOCUMENTATION_MAINTENANCE.md](docs/DOCUMENTATION_MAINTENANCE.md)
2. 按 [Diátaxis](https://diataxis.fr/) 选择文档类型（Tutorial / How-to / Explanation / Reference）
3. 更新文档头部 **版本** 与 **更新日期**
4. 配置项变更须同步 [docs/reference/CONFIGURATION.md](docs/reference/CONFIGURATION.md)

---

## Pull Request 流程

1. 从 `main` 创建特性分支：`feat/xxx` 或 `fix/xxx`
2. 提交信息：简明说明 **为什么** 改（中文或英文均可）
3. 确保 CI 通过
4. 填写 PR 描述：变更摘要 + 测试方式
5. 等待 Review

---

## Issue 标签建议

| 标签 | 用途 |
|------|------|
| `bug` | 缺陷报告 |
| `enhancement` | 功能建议 |
| `documentation` | 文档问题 |
| `good first issue` | 新人友好 |

---

## 许可证

贡献代码以 [Apache License 2.0](LICENSE) 发布。

---

## 联系方式

- [Gitee Issues](https://gitee.com/zestcc/zestflow/issues)
- [GitHub Issues](https://github.com/zestcc/zestflow/issues)
