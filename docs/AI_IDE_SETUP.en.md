# ZestFlow AI-Assisted Development — Full IDE Setup Guide

> **Version** 1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](AI_IDE_SETUP.md) · **Type** Tutorial · [← Documentation hub](README.en.md)  
> **Positioning** One `zestflow-mcp.jar` adapts to mainstream AI coding scenarios  
> **Related** [MCP_SETUP.en.md](MCP_SETUP.en.md) · [AI_COPILOT.en.md](AI_COPILOT.en.md) · [guides/COMPONENT_DEVELOPMENT.en.md](guides/COMPONENT_DEVELOPMENT.en.md)

---

## 1. Architecture overview

```text
┌─────────────────────────────────────────────────────────────┐
│  AI client (choose one or more)                             │
│  Cursor · Claude Desktop · Claude Code · VS Code · Windsurf   │
└───────────────────────────┬─────────────────────────────────┘
                            │ MCP (stdio)
                            ▼
              ~/.zestflow/tools/zestflow-mcp.jar
                            │ --project / --executor-url
                            ▼
              Business app Executor Netty (:20550)
                            │
              {dataDir}/ai/  Knowledge base · RAG · suggest
```

| Capability | Provider |
|------------|----------|
| Write Java components, Apply to disk | **IDE model** (Cursor/Claude/GPT, etc.) |
| Specs, whitelist, validate, RAG | **zestflow-mcp** Tools |
| Learning events, pattern master store | **Executor** `{dataDir}/ai/` |
| Visual orchestration, chain publish | **Admin** (optional; not required for Dev) |

**Tagline**: Admin designs chains, MCP connects specs and code, IDE writes components.

---

## 2. One-time setup (per machine)

```powershell
# From zestflow repo root
mvn install -DskipTests
powershell -File scripts/dev/install-mcp.ps1
```

Artifact: `%USERPROFILE%\.zestflow\tools\zestflow-mcp.jar` (requires Java **17+**)

---

## 3. Per business project (recommended `--init-dev`)

```powershell
powershell -File scripts/dev/init-dev-project.ps1 `
  -ProjectRoot D:\work\my-app `
  -AppCode my-app `
  -Ide all
```

`--ide` options: `cursor` | `vscode` | `cline` | `claude` | `claude-code` | `windsurf` | `all` (default `all`)

### Generated artifacts comparison

| IDE / scenario | Config file | Scope | Commit to Git |
|----------------|-------------|-------|---------------|
| **Cursor** | `.cursor/mcp.json` | Project | ✅ Recommended |
| **VS Code Copilot** | `.vscode/mcp.json` | Project | ✅ |
| **Cline** | `.vscode/mcp.json` | Project | ✅ (same as VS Code) |
| **Claude Code CLI** | `.mcp.json` | Project | ✅ Recommended |
| **Claude Desktop** | `.zestflow/mcp/claude-desktop.config.json.example` | User directory | Example in repo |
| **Windsurf Cascade** | `.zestflow/mcp/windsurf.mcp_config.json.example` | **Global** | Example in repo |
| **General guide** | `.zestflow/mcp/README.md` | Project | ✅ |
| **Claude / general Agent** | `CLAUDE.md` | Project | ✅ |
| **Cursor Agent rules** | `.cursor/rules/zestflow-architecture.md` | Project | ✅ |
| **Copilot instructions** | `.github/copilot-instructions.md` | Project | ✅ |

Manual template copy: [`scripts/dev/mcp/`](../scripts/dev/mcp/README.md).

---

## 4. Per-IDE setup steps

### 4.1 Cursor (primary)

1. Open **business project root** in Cursor (contains `pom.xml`), not the zestflow monorepo root
2. Confirm `.cursor/mcp.json` exists and `app-code` matches `spring.application.name`
3. Start the business app (Executor `:20550`)
4. Settings → MCP → Refresh; use `@zestflow` in chat or let Agent call Tools
5. Smoke test: `list_components`

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

1. Copy `mcpServers.zestflow` from `.zestflow/mcp/claude-desktop.config.json.example`
2. Merge into user config:
   - **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
   - **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
3. Use **absolute path** for `--project` (Claude Desktop has no `${workspaceFolder}`)
4. Fully quit and restart Claude Desktop
5. Project rules: `CLAUDE.md` is read by Claude Code; Desktop chat can @ reference MCP Tools

---

### 4.3 Claude Code (CLI / terminal Agent)

1. Project root has **`.mcp.json`** (`--init-dev` or `--ide claude-code`)
2. Run `claude` from project directory
3. First use of project-scoped MCP prompts for approval
4. Verify: `/mcp` list includes `zestflow`
5. If missing: `claude mcp reset-project-choices` then restart session

`.mcp.json` example (includes `type: stdio`):

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

**Team collaborators**: After clone, paths differ; each person re-runs init-dev with `--force` or edits `PROJECT_PATH` manually.

---

### 4.4 VS Code + GitHub Copilot

1. Install VS Code MCP extension (or Copilot built-in MCP support)
2. Use `.vscode/mcp.json` (same format as Cursor; `${workspaceFolder}` works)
3. Copilot reads `.github/copilot-instructions.md`

---

### 4.5 Cline (VS Code extension)

Cline reads **`.vscode/mcp.json`**, shared with VS Code; no separate file needed.  
Optional: reference `CLAUDE.md` content in `.cline/rules`.

---

### 4.6 Windsurf (Codeium Cascade)

Windsurf **does not support project-level MCP**; only global config:

- **Windows**: `%USERPROFILE%\.codeium\windsurf\mcp_config.json`
- **macOS/Linux**: `~/.codeium/windsurf/mcp_config.json`

1. Cascade panel → MCP → **Configure** opens the file above
2. **Merge** entries from `.zestflow/mcp/windsurf.mcp_config.json.example`
3. `--project` must be **absolute path**; use different server names per project, e.g. `zestflow-my-app`
4. Save → MCP panel **Refresh**

---

### 4.7 Other scenarios

| Scenario | Recommendation |
|----------|----------------|
| **Remote Admin + local project** | Add `--admin-url` + `--token` (JWT) to MCP args |
| **No IDE, task package only** | `java -jar zestflow-mcp.jar --export-task-package --project ...` |
| **JetBrains AI** | No official MCP yet; use MCP export task package + paste specs manually |
| **OpenAI Codex / web** | MCP not applicable; use Admin Orchestration Copilot or export Markdown |

---

## 5. Optional: Executor-side LLM

IDE model handles **writing code**; Executor LLM enhances **chains/suggest** and **embedding RAG** (MCP `search_patterns` / suggest proxy).

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

When disabled, falls back to pattern + keyword Hybrid RAG; **does not affect basic MCP Tools**.

---

## 6. Recommended daily workflow

```text
1. Start business app (Executor online)
2. Open project in IDE; confirm MCP connected
3. Describe requirement → search_patterns → plan_chain
4. scaffold_component → validate_chain
5. You review / Apply code → mvn compile
6. record_learning_event (after successful test run)
7. When visualization needed → Admin designer publish chain
```

---

## 7. Troubleshooting

| Symptom | Fix |
|---------|-----|
| MCP not listed | Check JAR path, Java 17+, restart IDE |
| `list_components` fails | Executor not started or wrong `--executor-url` |
| Claude Code ignores `.mcp.json` | Start `claude` from project root; reset project choices |
| Windsurf no Tools | Config is global `mcp_config.json`, not project dir |
| 401 / 403 | Add `--executor-token` matching `zestflow.executor.access-token` |
| Paths with spaces | Use forward slashes or escape in JSON |

---

## 8. Related documentation

- [MCP_SETUP.en.md](MCP_SETUP.en.md) — Installation and Tool list
- [AI_COPILOT.en.md](AI_COPILOT.en.md) — Admin Orchestration Copilot (complements Dev MCP)
- [acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.en.md) — Executor AI v2 acceptance
- [HANDOFF-AI-EXECUTOR.md](HANDOFF-AI-EXECUTOR.en.md) — Cross-machine handoff
