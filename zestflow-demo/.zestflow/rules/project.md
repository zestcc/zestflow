# demo-app 项目级 Dev Copilot 规则（L2）

## 包与目录

- 业务元件放在 `com.zestflow.demo.component`
- 新元件类名以 `Handler` 或 `Component` 结尾
- 包路径: com.zestflow.demo.component

## 命名

- `componentId` 使用 camelCase，与 demo 现有元件风格一致（如 `getUserCache`）
- 分组名与现有 `@ZestComponent` 一致：`cache`、`order` 等
- 默认 appCode: **demo-app**

## Dev Copilot（MCP）

- 平台 JAR **一次安装**：`powershell -File scripts/dev/install-mcp.ps1` → `~/.zestflow/tools/zestflow-mcp.jar`
- Cursor 打开本目录即可（`.cursor/mcp.json` 已配置 `${workspaceFolder}`）
- 详见 [MCP_SETUP.md](../../docs/MCP_SETUP.md)

## Chain-first 学习（L2 项目）

- 学习事件：`.zestflow/learning/events.jsonl`（原始信号，可 gitignore）
- 蒸馏 Pattern：`.zestflow/patterns/`（建议提交 Git 团队继承）
- 工作流见 [AI_CHAIN_LEARNING.md](../../docs/AI_CHAIN_LEARNING.md)

## 禁止

- 不要修改 `zestflow-demo` 以外的模块除非用户明确要求
- 不要自动提交 Git 或调用 Admin 发布 API
- 源码落盘使用 Cursor Apply，不用 MCP 写盘
