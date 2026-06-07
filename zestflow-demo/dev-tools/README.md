# Dev Copilot（MCP）— 开发专用

> **不再在此目录存放 JAR。** 平台 MCP 统一安装到用户目录 `~/.zestflow/tools/zestflow-mcp.jar`。

## 一次性准备（全机 / 全团队各装一次）

```powershell
powershell -File scripts/dev/install-mcp.ps1
```

## demo 用法

1. 执行上述安装
2. Cursor **打开 `zestflow-demo` 文件夹**（已含 [`.cursor/mcp.json`](../.cursor/mcp.json)）
3. 启动 demo Executor（`:20550`）

## 其它业务项目

复制 [`scripts/dev/mcp/project.cursor.mcp.json.example`](../../scripts/dev/mcp/project.cursor.mcp.json.example) 到项目 `.cursor/mcp.json`，改 `app-code` 即可。

详见 [MCP_SETUP.md](../../docs/MCP_SETUP.md)、[scripts/dev/mcp/README.md](../../scripts/dev/mcp/README.md)。
