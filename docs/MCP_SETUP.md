# ZestFlow MCP 开发助手安装指南

> **适用** Cursor、Claude Desktop、VS Code MCP 扩展等任意 MCP 客户端  
> **架构** 见 [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.md)  
> **对标** [Model Context Protocol](https://modelcontextprotocol.io)、[Supabase MCP](https://github.com/supabase-community/supabase-mcp)、[Stripe MCP](https://github.com/stripe/agent-toolkit)

---

## 1. 构建 Dev MCP（**不进** demo/admin 试玩包）：

```powershell
powershell -File scripts/dev/setup-demo-mcp.ps1
```

产物：`zestflow-mcp/target/zestflow-mcp-0.1.0-all.jar`（含依赖的可执行包）。**默认 reactor 不含本模块**，需 `-Pdev-mcp`。

---

## 2. 启动参数

| 参数 | 必填 | 说明 |
|------|------|------|
| `--project` | 是 | Executor/业务工程根目录（含 `pom.xml`） |
| `--app-code` | 否 | 默认 `demo` |
| `--executor-url` | 否 | 默认 `http://127.0.0.1:20550`（本地 Executor Netty） |
| `--executor-token` | 否 | Executor `X-Access-Token` |
| `--admin-url` | 否 | 远程 Admin 根址，如 `https://admin.company.com` |
| `--token` | 否 | Admin Bearer Token（走 Admin 代理时用） |

环境变量等价：`ZESTFLOW_PROJECT`、`ZESTFLOW_APP_CODE`、`ZESTFLOW_EXECUTOR_URL`、`ZESTFLOW_ADMIN_URL`、`ZESTFLOW_TOKEN`。

**本地日常开发（仅起 Executor）：**

```bash
java -jar zestflow-mcp.jar \
  --project D:/work/zestflow/zestflow-demo \
  --app-code demo \
  --executor-url http://127.0.0.1:20550
```

**远程 Admin + 本地工程：**

```bash
java -jar zestflow-mcp.jar \
  --project D:/work/my-app \
  --admin-url https://admin.company.com \
  --token "YOUR_JWT" \
  --app-code my-app
```

---

## 3. Cursor 配置

文件：项目 `.cursor/mcp.json` 或用户 `~/.cursor/mcp.json`

```json
{
  "mcpServers": {
    "zestflow": {
      "command": "java",
      "args": [
        "-jar", "D:/tools/zestflow-mcp-0.1.0-all.jar",
        "--project", "D:/work/zestflow/zestflow-demo",
        "--app-code", "demo",
        "--executor-url", "http://127.0.0.1:20550"
      ]
    }
  }
}
```

重启 Cursor 或在设置中刷新 MCP；对话中可 `@zestflow` 或让 Agent 自动调用 Tools。

### zestflow-demo 快速集成

1. `powershell -File scripts/dev/setup-demo-mcp.ps1` → JAR 复制到 `zestflow-demo/dev-tools/`
2. 用 Cursor **打开 `zestflow-demo` 目录**（已含 `.cursor/mcp.json`）
3. 启动 demo（Executor `:20550`）

---

## 4. Claude Desktop 配置

`claude_desktop_config.json`（路径见 [Anthropic 文档](https://modelcontextprotocol.io/docs/develop/connect-local-servers)）：

```json
{
  "mcpServers": {
    "zestflow": {
      "command": "java",
      "args": [
        "-jar", "/path/zestflow-mcp-0.1.0.jar",
        "--project", "/path/zestflow-demo",
        "--executor-url", "http://127.0.0.1:20550"
      ]
    }
  }
}
```

**同一 JAR、同一套 args** — 仅配置文件位置不同（与 Stripe/Supabase 官方 MCP 模式一致）。

---

## 5. MCP 能力一览

### Resources（规范，随 JAR 版本更新）

| URI | 内容 |
|-----|------|
| `zestflow://rules/component` | `@ZestComponent` 开发规范 |
| `zestflow://rules/chain` | 链定义约定 |
| `zestflow://rules/anti-patterns` | 禁止项 |
| `zestflow://rules/aviator` | Aviator 表达式 |
| `zestflow://rules/project` | **L1 官方摘要 + L2 项目规则** |
| `zestflow://schema/chain-definition` | JSON Schema |
| `zestflow://examples/component/execute` | Java 示例 |

**平台 Pattern（L0，JAR 内）**：`zestflow/patterns/platform/` — `plan_chain` / `search_patterns` 自动加载。项目 Pattern 见 `{project}/.zestflow/patterns/`。

### Tools（共 12 个）

**Phase 1 — 基础**

| Tool | 用途 |
|------|------|
| `list_components` | 元件白名单（禁止编造 id） |
| `read_project_file` | 读 `--project` 下源码 |
| `validate_chain` | Executor ChainValidator |

**Phase 2 — 开发辅助**

| Tool | 用途 |
|------|------|
| `search_sources` | 项目内关键词/glob 搜索 |
| `scaffold_component` | Java 脚手架（**仅返回文本**） |
| `export_task_package` | 导出任务包 Markdown（CLI 模式） |

**Phase 3 — Chain-first 学习与沉淀（P1～P3）**

| Tool | 用途 | 层级 |
|------|------|------|
| `plan_chain` | 意图 → 业务链规划 + 元件 gap | 平台模板 + 项目 Pattern |
| `record_learning_event` | 记录采纳/验证结果 → `events.jsonl` | L3 原始信号 |
| `search_patterns` | 检索平台(L0) + 项目(L2) Pattern | L0/L2 |
| `distill_patterns` | 高置信事件蒸馏 → `.zestflow/patterns/` | L2 项目 |
| `gen_playground_scene` | 生成 Playground 场景 JSON | 项目 |
| `share_pattern` | 导出 Pattern JSON → Admin RAG import | L1 团队 |

**推荐链式调用**（见 [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.md)）：

```text
plan_chain → scaffold_component(gap) → validate_chain → gen_playground_scene
  → record_learning_event → distill_patterns → share_pattern
```

**97% 准确率**：非 LLM 自评；须 `validate_chain` 通过 + `AccuracyGate` 晋升门槛后才进入 Pattern/RAG。

**不提供** `write_project_file`：写源码由 IDE 确认后落盘，见 [最终方案 §2.3](./AI_DEV_COPILOT_FINAL_SOLUTION.md#23-dev-copilot-明确不做v1)。

**审计**：Tool 调用默认追加到 `{project}/.zestflow/mcp-audit.jsonl`；`--no-audit-log` 可关闭。

**CLI 任务包**（不启 MCP Server）：

```bash
java -jar zestflow-mcp-0.1.0-all.jar \
  --export-task-package \
  --project D:/work/zestflow-demo \
  --app-code demo \
  -o zestflow-task.md
```

### 项目自定义规则（L2）

在工程根创建：

```text
.zestflow/rules/project.md
```

MCP 通过 `zestflow://rules/project` 合并官方规范与项目规则。**L0 平台硬约束不可被覆盖。**

---

## 6. 推荐工作流

```text
1. 用户：「帮我开发注册链路」
2. 模型：search_patterns / plan_chain（检索平台+项目经验）
3. 模型：list_components → scaffold_component（补 gap 元件）
4. 模型：validate_chain → gen_playground_scene
5. 模型：record_learning_event（采纳后）→ distill_patterns（高置信）
6. 团队：share_pattern → Admin RAG import 或 promote-rag
7. 开发者：mvn compile → 部署 → Admin 人工 publish/reload
```

---

## 7. 故障排查

| 现象 | 处理 |
|------|------|
| MCP 未出现在 Cursor | 检查 Java 17+、JAR 路径、重启 IDE |
| `list_components` 失败 | 确认 Executor 已启动且 `--executor-url` 正确 |
| 远程 Admin 401 | 补 `--token` 或换 `--executor-url` 直连 |
| 日志污染 MCP | 本 Server 日志走 **stderr**，勿改 stdout |

---

## 8. 相关文档

- [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.md) — P1～P3 学习与沉淀  
- [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.md) — 最终方案  
- [AI_DEV_COPILOT_ACADEMIC_SUMMARY.md](./AI_DEV_COPILOT_ACADEMIC_SUMMARY.md) — 方案研讨  
- [AI_COPILOT.md](./AI_COPILOT.md) — Admin Orchestration Copilot（已实现）
