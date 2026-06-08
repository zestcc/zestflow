# ZestFlow AI / Executor Knowledge Base — Cross-Machine Handoff Summary

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](HANDOFF-AI-EXECUTOR.md) · **Type** Handoff · [← Documentation hub](README.en.md)  
> For continuing development or verification after pulling code on another machine.  
> Remote: `https://gitee.com/zestcc/zestflow.git`, branch **`master`**.

---

## I. Architecture Consensus (Must Follow)

```
Admin Copilot / MCP (orchestration + LLM)
        ↓ RAG search / learning events / suggest proxy
Executor {dataDir}/ai/          ← Primary chain knowledge base
        ├── learning/events.jsonl
        └── patterns/*.md       ← High-confidence auto-distillation
```

| Responsibility | Owner |
|----------------|-------|
| Learning events, distillation, patterns primary store | **Application-side Executor** |
| LLM generation, quality gates, canvas UI | **Admin** (generic acceptance, no business hardcoding) |
| Tenant RAG / DB learning events | Admin **audit + optional supplement** (`tenant-rag-auto-promote: false`) |

---

## II. Git Commits (Reverse Chronological)

| Commit | Description |
|--------|-------------|
| `cf3d67d` | Admin UI: fullscreen Playground trial feedback, Executor RAG settings panel, Vite chunking, CI dev-init |
| `5b34f12` | Admin/MCP: proxy Executor status/suggest; MCP requires `--executor-url` |
| `48f04cd` | Executor AI: local auth, dedup, post-validate distillation, suggest, RAG token scoring |
| `cac0d84` | First sink: Executor knowledge base, dev-init incremental config, ai-generation-acceptance, dagre layout, etc. |

**First step on new machine:**

```bash
git clone https://gitee.com/zestcc/zestflow.git
cd zestflow
git pull origin master
```

---

## III. New Machine Environment Setup

### Dependencies

- JDK **17**
- Maven **3.8+**
- Node **20+** (admin-ui build)
- Optional: local MySQL, Ollama (Admin Copilot)

### Build and Test (Recommended Order)

```bash
# 1. Install dev-templates (dev-init tests depend on it)
mvn install -pl zestflow-dev-templates -DskipTests

# 2. Core module tests
cd zestflow-executor && mvn test -Dtest=ExecutorChainAiServiceTest
cd ../zestflow-admin && mvn test -Dtest=AiCopilotServiceTest,AiCopilotControllerTest,AiLearningEventServiceTest
cd ../zestflow-mcp && mvn test -Dtest=AccuracyGateTest
cd .. && mvn test -pl zestflow-dev-init -am

# 3. Frontend
cd zestflow-admin-ui && npm install && npm run build
```

### Startup (Local Integration)

1. **Business app + Executor** (e.g. demo, port usually `20550`)
2. **Admin** (`8080`)
3. MCP config must include:
   - `--executor-url http://127.0.0.1:20550`
   - `--project <business project root>`
   - If Executor has token: `--executor-access-token` matches `zestflow.executor.access-token`

---

## IV. Key API Overview

### Executor (Application Side)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ai/rag/status` | Knowledge base status (event count, pattern count) |
| GET | `/api/ai/rag/search?q=&limit=` | RAG search |
| POST | `/api/ai/learning/events` | Learning event (dedup + high-confidence auto-distill) |
| POST | `/api/ai/patterns/distill` | Manual distillation |
| POST | `/api/ai/chains/suggest` | Pattern-based chain draft (**non-LLM**) |

**Authentication:**

- If `zestflow.executor.access-token` configured → request header `X-Access-Token`
- If no token → default `ai-localhost-only=true`, only localhost can access `/api/ai/*`

### Admin (Proxy)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ai/rag/status?appCode=` | Tenant RAG + **Executor primary store status** |
| POST | `/api/ai/executor/chains/suggest` | Proxy Executor suggest |
| POST | `/api/ai/sessions/{id}/feedback` | Adoption/trial success → forward to Executor |

---

## V. Key Code Paths

| Module | Path |
|--------|------|
| Executor knowledge base | `zestflow-executor/src/main/java/com/zestflow/executor/ai/ExecutorChainAiService.java` |
| Chain validate injection | `zestflow-executor/.../ai/ChainDataValidator.java` + `ExecutorAutoConfig.java` |
| Executor routing | `zestflow-executor/.../server/ServerHandler.java` |
| Admin proxy | `zestflow-admin/.../ai/ExecutorChainAiClient.java` |
| Copilot | `zestflow-admin/.../ai/AiCopilotService.java` |
| MCP learning | `zestflow-mcp/.../learning/LearningToolService.java` |
| Frontend Copilot | `zestflow-admin-ui/src/stores/aiCopilot.ts` |
| Fullscreen Playground feedback | `zestflow-admin-ui/src/views/playground/PlaygroundPage.vue` |
| Executor RAG panel | `zestflow-admin-ui/src/components/settings/SettingsAiRagPanel.vue` |
| Canvas layout | `zestflow-admin-ui/src/utils/chainApply.ts` (dagre) |
| Acceptance rules (three-tier same source) | `**/ai-generation-acceptance.md` |
| dev-init | `zestflow-dev-init/` + `zestflow-dev-templates/` |

---

## VI. Configuration Highlights

**Admin `application.yml`:**

```yaml
zestflow:
  ai:
    tenant-rag-auto-promote: false   # Tenant RAG no auto-promote; primary store on Executor
  admin:
    executor-access-token: <matches Executor access-token>
```

**Executor:**

```yaml
zestflow:
  executor:
    access-token: <required in production>
    ai-localhost-only: true          # Default true
    data-dir: ./zestflow-data        # Knowledge base at {dataDir}/ai/
```

**MCP (config file per IDE, see [AI_IDE_SETUP.en.md](./AI_IDE_SETUP.en.md)):**

| IDE | File |
|-----|------|
| Cursor | `.cursor/mcp.json` |
| Claude Code | `.mcp.json` |
| VS Code / Cline | `.vscode/mcp.json` |
| Claude Desktop | User dir `claude_desktop_config.json` |
| Windsurf | Global `mcp_config.json` |

```json
"args": [
  "-jar", ".../zestflow-mcp.jar",
  "--project", "${workspaceFolder}",
  "--executor-url", "http://127.0.0.1:20550",
  "--executor-token", "..."
]
```

> MCP learning/search/distill **requires** `--executor-url`; no longer uses local `.zestflow/` as primary path.

---

## VII. Learning Closed Loop (End-to-End)

```
1. Copilot suggest (Admin LLM + Executor RAG)
2. validate-definition (Executor)
3. Adoption apply → submitFeedback(adopted=true)
   or trial success → playgroundSuccess=true (embedded/fullscreen Playground)
4. POST Executor /api/ai/learning/events
5. High confidence → auto distill → patterns/*.md
6. Next search RAG reuses
```

---

## VIII. Done vs Pending

### Completed

- [x] Knowledge base sunk to Executor
- [x] Admin/MCP proxy + MCP requires executor-url
- [x] Learning event dedup, validate before distill
- [x] Playground full-chain trial feedback
- [x] AI settings page shows Executor knowledge base
- [x] dagre canvas layout, quality gates, dev-init incremental config
- [x] CI adds `zestflow-dev-init -am`

### Recommended Next on New Machine

1. ~~**Executor-side LLM suggest**~~ ✅ v2: OpenAI-compatible LLM + quality gates + validate repair + pattern fallback
2. ~~**Embedding RAG**~~ ✅ v2: keyword / hybrid / TF-IDF + optional embedding rerank
3. ~~**Complex CONDITION layout**~~ ✅ v2: True left / False right branch offset (chainApply)
4. **Business projects (e.g. zestory)** — run `--init-dev`, restore corrupted `application.yml` (in business repo)

Acceptance: `docs/acceptance/AI_EXECUTOR_V2_ACCEPTANCE.en.md`

---

## IX. Continuation Prompt for AI on Another Machine

```
Continuing AI/Executor knowledge base work on zestflow master.

Architecture: chain knowledge base primary path on Executor {dataDir}/ai/; Admin only LLM + proxy;
MCP must use --executor-url; tenant-rag-auto-promote=false.

Priority: Executor-side LLM chains/suggest (align with Admin Copilot),
or Embedding RAG. Read ExecutorChainAiService, ExecutorChainAiClient,
LearningToolService, ai-generation-acceptance.md first.

Verify: ExecutorChainAiServiceTest, AiCopilotServiceTest, mvn test -pl zestflow-dev-init -am, npm run build.
```
