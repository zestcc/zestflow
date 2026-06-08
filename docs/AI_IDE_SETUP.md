# ZestFlow AI 辅助开发 — 全 IDE 接入指南

> **版本** 1.0 · **更新** 2026-06-08 · **类型** Tutorial · [← 文档中心](README.md) · [English](AI_IDE_SETUP.en.md)
> **定位** 一套 `zestflow-mcp.jar` 适配市面主流 AI 编程场景  
> **关联** [MCP_SETUP.md](MCP_SETUP.md) · [AI_COPILOT.md](AI_COPILOT.md) · [guides/COMPONENT_DEVELOPMENT.md](guides/COMPONENT_DEVELOPMENT.md)

---

## 1. 架构一览

```text
┌─────────────────────────────────────────────────────────────┐
│  AI 客户端（任选其一或多）                                    │
│  Cursor · Claude Desktop · Claude Code · VS Code · Windsurf │
└───────────────────────────┬─────────────────────────────────┘
                            │ MCP（stdio）
                            ▼
              ~/.zestflow/tools/zestflow-mcp.jar
                            │ --project / --executor-url
                            ▼
              业务应用 Executor Netty（:20550）
                            │
              {dataDir}/ai/  知识库 · RAG · suggest
```

| 能力 | 谁提供 |
|------|--------|
| 写 Java 元件、Apply 落盘 | **IDE 自带模型**（Cursor/Claude/GPT 等） |
| 规范、白名单、validate、RAG | **zestflow-mcp** Tools |
| 学习事件、patterns 主库 | **Executor** `{dataDir}/ai/` |
| 可视化编排、发布链 | **Admin**（可选，非 Dev 必需） |

**口号**：Admin 设计链，MCP 连规范与代码，IDE 写元件。

---

## 2. 一次性准备（每台机器）

```powershell
# 在 zestflow 仓库根目录
mvn install -DskipTests
powershell -File scripts/dev/install-mcp.ps1
```

产物：`%USERPROFILE%\.zestflow\tools\zestflow-mcp.jar`（Java **17+** 运行）

---

## 3. 每个业务工程（推荐 `--init-dev`）

```powershell
powershell -File scripts/dev/init-dev-project.ps1 `
  -ProjectRoot D:\work\my-app `
  -AppCode my-app `
  -Ide all
```

`--ide` 可选：`cursor` | `vscode` | `cline` | `claude` | `claude-code` | `windsurf` | `all`（默认 `all`）

### 生成物对照表

| IDE / 场景 | 配置文件 | 作用域 | 提交 Git |
|------------|----------|--------|----------|
| **Cursor** | `.cursor/mcp.json` | 项目 | ✅ 推荐 |
| **VS Code Copilot** | `.vscode/mcp.json` | 项目 | ✅ |
| **Cline** | `.vscode/mcp.json` | 项目 | ✅（同 VS Code） |
| **Claude Code CLI** | `.mcp.json` | 项目 | ✅ 推荐 |
| **Claude Desktop** | `.zestflow/mcp/claude-desktop.config.json.example` | 用户目录 | 示例在仓库 |
| **Windsurf Cascade** | `.zestflow/mcp/windsurf.mcp_config.json.example` | **全局** | 示例在仓库 |
| **通用说明** | `.zestflow/mcp/README.md` | 项目 | ✅ |
| **Claude / 通用 Agent** | `CLAUDE.md` | 项目 | ✅ |
| **Cursor Agent 规则** | `.cursor/rules/zestflow-architecture.md` | 项目 | ✅ |
| **Copilot 指令** | `.github/copilot-instructions.md` | 项目 | ✅ |

手动复制模板见 [`scripts/dev/mcp/`](../scripts/dev/mcp/README.md)。

---

## 4. 分 IDE 接入步骤

### 4.1 Cursor（主推）

1. Cursor **打开业务工程根**（含 `pom.xml`），不是 zestflow  monorepo 根
2. 确认 `.cursor/mcp.json` 存在且 `app-code` = `spring.application.name`
3. 启动业务应用（Executor `:20550`）
4. 设置 → MCP → 刷新；对话可用 `@zestflow` 或让 Agent 自动调 Tool
5. 试跑：`list_components`

```json
{
  "mcpServers": {
    "zestflow": {
      "command": "java",
      "args": [
        "-jar", "${userHome}/.zestflow/tools/zestflow-mcp.jar",
        "--project", "${workspaceFolder}",
        "--app-code", "my-app",
        "--executor-url", "http://127.0.0.1:20550"
      ]
    }
  }
}
```

---

### 4.2 Claude Desktop

1. 从 `.zestflow/mcp/claude-desktop.config.json.example` 复制 `mcpServers.zestflow`
2. 合并到用户配置：
   - **Windows**：`%APPDATA%\Claude\claude_desktop_config.json`
   - **macOS**：`~/Library/Application Support/Claude/claude_desktop_config.json`
3. `--project` 使用**绝对路径**（Claude Desktop 无 `${workspaceFolder}`）
4. 完全退出并重启 Claude Desktop
5. 项目规范：同目录 `CLAUDE.md` 会被 Claude Code 读取；Desktop 对话中可 @ 引用 MCP Tools

---

### 4.3 Claude Code（CLI / 终端 Agent）

1. 项目根已有 **`.mcp.json`**（`--init-dev` 或 `--ide claude-code` 生成）
2. 在工程目录执行 `claude`
3. 首次使用 project-scoped MCP 会提示批准
4. 验证：`/mcp` 列表含 `zestflow`
5. 若未出现：`claude mcp reset-project-choices` 后重启会话

`.mcp.json` 示例（含 `type: stdio`）：

```json
{
  "mcpServers": {
    "zestflow": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar", "${userHome}/.zestflow/tools/zestflow-mcp.jar",
        "--project", "D:/work/my-app",
        "--app-code", "my-app",
        "--executor-url", "http://127.0.0.1:20550"
      ]
    }
  }
}
```

**团队协作者**：克隆仓库后路径不同，每人 `--force` 重跑 init-dev 或手改 `PROJECT_PATH`。

---

### 4.4 VS Code + GitHub Copilot

1. 安装 VS Code MCP 扩展（或 Copilot 内置 MCP 支持）
2. 使用 `.vscode/mcp.json`（与 Cursor 格式相同，`${workspaceFolder}` 可用）
3. Copilot 读取 `.github/copilot-instructions.md`

---

### 4.5 Cline（VS Code 扩展）

Cline 读取 **`.vscode/mcp.json`**，与 VS Code 共用配置；无需单独文件。  
规则可额外在 `.cline/rules` 中引用 `CLAUDE.md` 内容（可选）。

---

### 4.6 Windsurf（Codeium Cascade）

Windsurf **不支持项目级 MCP**，只能编辑全局文件：

- **Windows**：`%USERPROFILE%\.codeium\windsurf\mcp_config.json`
- **macOS/Linux**：`~/.codeium/windsurf/mcp_config.json`

1. Cascade 面板 → MCP → **Configure** 打开上述文件
2. 将 `.zestflow/mcp/windsurf.mcp_config.json.example` 中条目**合并**进去
3. `--project` 必须为**绝对路径**；多项目时用不同 server 名如 `zestflow-my-app`
4. 保存 → MCP 面板 **Refresh**

---

### 4.7 其它场景

| 场景 | 建议 |
|------|------|
| **远程 Admin + 本地工程** | MCP args 加 `--admin-url` + `--token`（JWT） |
| **无 IDE、只要任务包** | `java -jar zestflow-mcp.jar --export-task-package --project ...` |
| **JetBrains AI** | 暂无官方 MCP；用 MCP 导出任务包 + 手动粘贴规范 |
| **OpenAI Codex / 网页** | 不适用 MCP；用 Admin Orchestration Copilot 或导出 Markdown |

---

## 5. 可选：Executor 侧 LLM

IDE 模型负责**写代码**；Executor LLM 增强 **chains/suggest** 与 **embedding RAG**（MCP `search_patterns` / suggest 代理）。

```yaml
zestflow:
  executor:
    ai:
      llm-enabled: true
      base-url: http://localhost:11434/v1
      model: llama3.2
      embedding-model: nomic-embed-text
      rag-mode: hybrid
      rag-use-embedding: true
```

未启用时自动回落 pattern + 关键词 Hybrid RAG，**不影响 MCP 基础 Tool**。

---

## 6. 推荐日常流程

```text
1. 启动业务应用（Executor 在线）
2. IDE 打开工程，确认 MCP 已连接
3. 描述需求 → search_patterns → plan_chain
4. scaffold_component → validate_chain
5. 你 Review / Apply 代码 → mvn compile
6. record_learning_event（试跑成功后）
7. 需要可视化时 → Admin 设计器发布链
```

---

## 7. 故障排查

| 现象 | 处理 |
|------|------|
| MCP 未出现 | 检查 JAR 路径、Java 17+、重启 IDE |
| `list_components` 失败 | Executor 未启动或 `--executor-url` 错误 |
| Claude Code 不加载 `.mcp.json` | 在项目根启动 `claude`；重置 project choices |
| Windsurf 无 Tools | 配置在全局 `mcp_config.json`，非项目目录 |
| 401 / 403 | 补 `--executor-token` 与 `zestflow.executor.access-token` 一致 |
| 路径含空格 | JSON 中路径用正斜杠或转义 |

---

## 8. 相关文档

- [MCP_SETUP.md](MCP_SETUP.md) — 安装与 Tool 清单
- [AI_COPILOT.md](AI_COPILOT.md) — Admin 编排 Copilot（与 Dev MCP 互补）
- [acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md) — Executor AI v2 验收
- [HANDOFF-AI-EXECUTOR.md](HANDOFF-AI-EXECUTOR.md) — 跨机续跑
