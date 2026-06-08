# ZestFlow MCP Dev Assistant Setup Guide

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](MCP_SETUP.md) · **Type** Tutorial · [← Documentation hub](README.en.md)  
> **Applies to** Cursor, Claude Desktop, Claude Code, VS Code / Cline, Windsurf, and any MCP client  
> **Full IDE comparison** [AI_IDE_SETUP.en.md](./AI_IDE_SETUP.en.md) · **Architecture** [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.en.md)  
> **Reference** [Model Context Protocol](https://modelcontextprotocol.io), [Supabase MCP](https://github.com/supabase-community/supabase-mcp), [Stripe MCP](https://github.com/stripe/agent-toolkit)

---

## 1. Install platform MCP (**once**, shared across projects)

Modeled after Stripe / Supabase: **one JAR in the user directory**; each business project only configures `.cursor/mcp.json`.

```powershell
powershell -File scripts/dev/install-mcp.ps1
```

```bash
bash scripts/dev/install-mcp.sh
```

Artifacts:

| Path | Description |
|------|-------------|
| `~/.zestflow/tools/zestflow-mcp.jar` | Stable entry (referenced by Cursor config) |
| `~/.zestflow/tools/zestflow-mcp-0.1.0-all.jar` | Versioned backup |
| `zestflow-mcp/target/*.jar` | Maven build output (development) |

`zestflow-mcp` is included in the root `pom.xml` default `<modules>`; reload Maven in IDEA to recognize it; `mvn package` builds the MCP fat JAR.  
Legacy command `setup-demo-mcp.ps1` still works and forwards to `install-mcp.ps1`.

> **Do not** copy the JAR into each project's `dev-tools/`.

---

## 2. Startup parameters

| Parameter | Required | Description |
|-----------|----------|-------------|
| `--project` | Yes | Executor/business project root (contains `pom.xml`) |
| `--app-code` | No | Default `demo` |
| `--executor-url` | No | Default `http://127.0.0.1:20550` (local Executor Netty) |
| `--executor-token` | No | Executor `X-Access-Token` |
| `--admin-url` | No | Remote Admin base URL, e.g. `https://admin.company.com` |
| `--token` | No | Admin Bearer token (when proxying via Admin) |

Environment variable equivalents: `ZESTFLOW_PROJECT`, `ZESTFLOW_APP_CODE`, `ZESTFLOW_EXECUTOR_URL`, `ZESTFLOW_ADMIN_URL`, `ZESTFLOW_TOKEN`.

### One-click Dev file initialization (recommended)

After installing the platform JAR, run from the **business project root**:

```bash
java -jar ~/.zestflow/tools/zestflow-mcp.jar --init-dev --project /path/to/my-app
```

```powershell
powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot D:/work/my-app
```

| Parameter | Description |
|-----------|-------------|
| `--init-dev` | Generate architecture specs (see table below), IDE MCP config, learning directory |
| `--ide` | `cursor` / `vscode` / `cline` / `claude` / `claude-code` / `windsurf` / `all` (default `all`) |
| `--base-package` | Override package name inferred from pom |
| `--force` | Overwrite existing files |
| `--no-gitignore` | Do not append `.gitignore` entries |

**`--init-dev` artifacts (same architecture, cross-IDE):**

| File | Purpose |
|------|---------|
| `.zestflow/rules/architecture.md` | **Spec source** (IDE-agnostic): componentization, layering, prohibitions |
| `.zestflow/rules/project.md` | MCP L2: Chain-first, learning accumulation |
| `.cursor/rules/zestflow-architecture.md` | Cursor Agent auto-load |
| `.github/copilot-instructions.md` | VS Code Copilot |
| `CLAUDE.md` | Claude Desktop / Claude Code |
| `.cursor/mcp.json`, etc. | MCP connection config |

To change architecture, edit `architecture.md` then `--init-dev --force`, or edit the spec source and sync IDE files.

**Business projects using Maven `zestflow-starter`**: runtime via starter; Dev files via the command above (one-time). MCP has no hard binding to Cursor.

**Local daily development (Executor only):**

```bash
java -jar zestflow-mcp.jar \
  --project D:/work/zestflow/zestflow-demo \
  --app-code demo \
  --executor-url http://127.0.0.1:20550
```

**Remote Admin + local project:**

```bash
java -jar zestflow-mcp.jar \
  --project D:/work/my-app \
  --admin-url https://admin.company.com \
  --token "YOUR_JWT" \
  --app-code my-app
```

---

## 3. Cursor configuration

### Recommended: project-level (`${workspaceFolder}` points to current project)

Each business project root `.cursor/mcp.json` (auto-generated via `java -jar ... --init-dev --project .`, or copy from [`scripts/dev/mcp/project.cursor.mcp.json.example`](../scripts/dev/mcp/project.cursor.mcp.json.example)):

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

| Placeholder | Meaning |
|-------------|---------|
| `${userHome}/.zestflow/tools/zestflow-mcp.jar` | **Platform JAR** (`install-mcp`; shared) |
| `${workspaceFolder}` | **Current Cursor workspace root** (contains `pom.xml`) |
| `--app-code` | **This project's** Executor appCode |

Restart Cursor or refresh MCP in settings; use `@zestflow` in chat or let Agent call Tools automatically.

### zestflow-demo quick start

1. `powershell -File scripts/dev/install-mcp.ps1` (**once per machine**)
2. Open **`zestflow-demo`** directory in Cursor (includes `.cursor/mcp.json`)
3. Start demo Executor (`:20550`)

### Optional: user-level global template

For multiple projects sharing the same `executor-url`, use `~/.cursor/mcp.json`; still prefer `${workspaceFolder}` for `--project` to avoid hard-coded paths.

---

## 4. Claude Desktop configuration

> Claude **Code** uses project root `.mcp.json`; see [AI_IDE_SETUP.en.md §4.3](./AI_IDE_SETUP.en.md#43-claude-code-cli--terminal-agent).

`claude_desktop_config.json` (path per [Anthropic docs](https://modelcontextprotocol.io/docs/develop/connect-local-servers)):

```json
{
  "mcpServers": {
    "zestflow": {
      "command": "java",
      "args": [
        "-jar", "${userHome}/.zestflow/tools/zestflow-mcp.jar",
        "--project", "${workspaceFolder}",
        "--executor-url", "http://127.0.0.1:20550"
      ]
    }
  }
}
```

**Same JAR, same args** — only config file location differs (same pattern as Stripe/Supabase official MCP).

### Windsurf / Claude Code

| Client | Config location |
|--------|-----------------|
| Claude Code | Project root `.mcp.json` (`--init-dev` generates) |
| Windsurf | Global `~/.codeium/windsurf/mcp_config.json` (merge `.zestflow/mcp/windsurf.mcp_config.json.example`) |

See [AI_IDE_SETUP.en.md](./AI_IDE_SETUP.en.md).

---

## 5. MCP capabilities overview

### Resources (specs, updated with JAR version)

| URI | Content |
|-----|---------|
| `zestflow://rules/component` | `@ZestComponent` development rules |
| `zestflow://rules/chain` | Chain definition conventions |
| `zestflow://rules/anti-patterns` | Prohibited patterns |
| `zestflow://rules/aviator` | Aviator expressions |
| `zestflow://rules/project` | **L1 official summary + L2 project rules** |
| `zestflow://schema/chain-definition` | JSON Schema |
| `zestflow://examples/component/execute` | Java example |

**Platform patterns (L0, in JAR)**: `zestflow/patterns/platform/` — auto-loaded by `plan_chain` / `search_patterns`. Project patterns: `{project}/.zestflow/patterns/`.

### Tools (12 total)

**Phase 1 — Basics**

| Tool | Purpose |
|------|---------|
| `list_components` | Component whitelist (do not invent IDs) |
| `read_project_file` | Read source under `--project` |
| `validate_chain` | Executor ChainValidator |

**Phase 2 — Dev assistance**

| Tool | Purpose |
|------|---------|
| `search_sources` | Keyword/glob search in project |
| `scaffold_component` | Java scaffold (**returns text only**) |
| `export_task_package` | Export task package Markdown (CLI mode) |

**Phase 3 — Chain-first learning (P1–P3)**

| Tool | Purpose | Layer |
|------|---------|-------|
| `plan_chain` | Intent → business chain plan + component gaps | Platform template + project pattern |
| `record_learning_event` | Record adoption/validation → `events.jsonl` | L3 raw signals |
| `search_patterns` | Search platform (L0) + project (L2) patterns | L0/L2 |
| `distill_patterns` | Distill high-confidence events → `.zestflow/patterns/` | L2 project |
| `gen_playground_scene` | Generate Playground scene JSON | Project |
| `share_pattern` | Export pattern JSON → Admin RAG import | L1 team |

**Recommended chain** (see [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.en.md)):

```text
plan_chain → scaffold_component(gap) → validate_chain → gen_playground_scene
  → record_learning_event → distill_patterns → share_pattern
```

**97% accuracy**: Not LLM self-assessment; requires `validate_chain` pass + `AccuracyGate` promotion threshold before entering Pattern/RAG.

**No** `write_project_file`: source writes are done via IDE diff + user Apply; see [final solution §2.3](./AI_DEV_COPILOT_FINAL_SOLUTION.md#23-dev-copilot-明确不做v1).

**Audit**: Tool calls append to `{project}/.zestflow/mcp-audit.jsonl` by default; `--no-audit-log` disables.

**CLI task package** (without MCP Server):

```bash
java -jar zestflow-mcp-0.1.0-all.jar \
  --export-task-package \
  --project D:/work/zestflow-demo \
  --app-code demo \
  -o zestflow-task.md
```

### Project custom rules (L2)

Create at project root:

```text
.zestflow/rules/project.md
```

MCP merges official rules with project rules via `zestflow://rules/project`. **L0 platform hard constraints cannot be overridden.**

---

## 6. Recommended workflow

```text
1. User: "Help me build a registration chain"
2. Model: search_patterns / plan_chain (platform + project experience)
3. Model: list_components → scaffold_component (fill component gaps)
4. Model: validate_chain → gen_playground_scene
5. Model: record_learning_event (after adoption) → distill_patterns (high confidence)
6. Team: share_pattern → Admin RAG import or promote-rag
7. Developer: mvn compile → deploy → Admin manual publish/reload
```

---

## 7. Troubleshooting

| Symptom | Fix |
|---------|-----|
| MCP not appearing in Cursor | Run `install-mcp.ps1`; verify `~/.zestflow/tools/zestflow-mcp.jar` exists; Java 17+ |
| `list_components` fails | Confirm Executor is running and `--executor-url` is correct |
| Remote Admin 401 | Add `--token` or use `--executor-url` direct |
| Logs polluting MCP | Server logs go to **stderr**; do not redirect stdout |

---

## 8. Related documentation

- [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.en.md) — P1–P3 learning and accumulation  
- [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.en.md) — Final solution  
- [AI_DEV_COPILOT_ACADEMIC_SUMMARY.md](./AI_DEV_COPILOT_ACADEMIC_SUMMARY.en.md) — Solution discussion  
- [AI_COPILOT.en.md](./AI_COPILOT.en.md) — Admin Orchestration Copilot (implemented)
