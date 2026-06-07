# MCP 接入模板（平台 JAR + 项目配置）

## 分层（对标 Stripe / Supabase MCP）

| 层级 | 内容 | 频率 |
|------|------|------|
| **平台** | `zestflow-mcp.jar` → `~/.zestflow/tools/` | 装一次 / 升级时 |
| **项目** | `.cursor/mcp.json` + `.zestflow/rules/project.md` | 每个业务工程（**可 `--init-dev` 自动生成**） |

模板资源随 `zestflow-starter` / `zestflow-dev-templates` 分发；初始化由 MCP CLI 写入项目目录。

## 1. 安装平台 JAR（一次）

```powershell
# Windows
powershell -File scripts/dev/install-mcp.ps1

# macOS / Linux
bash scripts/dev/install-mcp.sh
```

## 2. 业务项目接入（推荐：一键初始化）

```powershell
powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot D:/work/my-app
```

或：

```bash
java -jar ~/.zestflow/tools/zestflow-mcp.jar --init-dev --project /path/to/my-app
```

可选参数：`--app-code`、`--executor-url`、`--base-package`、`--ide cursor|vscode|claude|all`（默认 `all`）、`--force`。

会从 `application.yml` / `pom.xml` 推断 appCode、Executor 端口与包名，并生成：

- `.zestflow/rules/project.md`
- `.cursor/mcp.json`（及 VS Code / Claude 示例）
- `.zestflow/learning/` 目录

### 手动复制（旧方式）

```powershell
mkdir your-project\.cursor -Force
copy scripts\dev\mcp\project.cursor.mcp.json.example your-project\.cursor\mcp.json
```

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
