# 本地 Dev Copilot（MCP）— 仅开发使用

本目录用于存放 **`zestflow-mcp` 可执行 JAR**，**不会**打进 `zestflow-demo` 生产/试玩 Spring Boot 包。

## 一次性准备

在仓库根目录执行：

```powershell
powershell -File scripts/dev/setup-demo-mcp.ps1
```

或：

```bash
mvn -Pdev-mcp -pl zestflow-mcp package -DskipTests
# 手动复制 target/zestflow-mcp-0.1.0-all.jar 到本目录
```

## Cursor 配置

已提供 [`../.cursor/mcp.json`](../.cursor/mcp.json)，打开 **`zestflow-demo` 文件夹** 作为工作区即可。

## 前置条件

1. 启动 `zestflow-demo`（Executor `:20550`）
2. Java 17+
3. Java 17+

## 说明

- 元件开发请用 **Cursor + MCP**（见 [MCP_SETUP.md](../../docs/MCP_SETUP.md)），Admin 内不提供 Dev 助手页
- 审计日志：`.zestflow/mcp-audit.jsonl`
- 项目规则：`.zestflow/rules/project.md`
