# MCP 接入模板（平台 JAR + 项目配置）

## 分层（对标 Stripe / Supabase MCP）

| 层级 | 内容 | 频率 |
|------|------|------|
| **平台** | `zestflow-mcp.jar` → `~/.zestflow/tools/` | 装一次 / 升级时 |
| **项目** | `.cursor/mcp.json` + `.zestflow/rules/project.md` | 每个业务工程 |

## 1. 安装平台 JAR（一次）

```powershell
# Windows
powershell -File scripts/dev/install-mcp.ps1

# macOS / Linux
bash scripts/dev/install-mcp.sh
```

## 2. 业务项目接入（复制模板）

```powershell
mkdir your-project\.cursor -Force
copy scripts\dev\mcp\project.cursor.mcp.json.example your-project\.cursor\mcp.json
```

编辑 `your-project/.cursor/mcp.json`：

- `YOUR_APP_CODE` → 实际 appCode（如 `demo-app`）
- `--executor-url` → 本地 Executor 或远程地址

**不要**把 JAR 复制进每个项目；`${userHome}/.zestflow/tools/zestflow-mcp.jar` 全项目共用。

## 3. 可选项目规则

```text
your-project/.zestflow/rules/project.md
your-project/.zestflow/patterns/     # 蒸馏沉淀（可选）
```

## 远程 Admin 代理（可选）

在 `args` 中增加：

```json
"--admin-url", "https://admin.company.com",
"--token", "YOUR_JWT"
```

或使用环境变量：`ZESTFLOW_ADMIN_URL`、`ZESTFLOW_TOKEN`（见 MCP_SETUP.md）。
