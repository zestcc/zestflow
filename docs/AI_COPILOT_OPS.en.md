# ZestFlow Copilot Operations Guide

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](AI_COPILOT_OPS.md) · **Type** How-to · [← Documentation hub](README.en.md) · Use with [AI_COPILOT.en.md](./AI_COPILOT.en.md) P5

This guide is for platform operators and tenant administrators. It covers AI preset maintenance, tenant RAG knowledge bases, usage dashboards, and common deployment configuration.

---

## 1. Global Switches and Configuration Sources

### 1.1 Runtime Priority

```
Effective value = sys_config (tenant 1, editable in Admin UI, hot reload) > application.yml cold-start fallback
```

| Layer | Storage | Description |
|-------|---------|-------------|
| Deployment / secrets | yaml + environment variables | JWT, registry-token, SMTP, `env-keys`, etc. |
| Enums / cascades | Dictionary `ai_provider` / `ai_model` | Provider and model lists |
| Platform-tunable parameters | `sys_config` (tenant 1) | AI switch, RAG thresholds, Playground timeout, etc. |
| Tenant AI runtime | `zf_ai_tenant_config` | Per-tenant preset, Key, quota |

**Operations entry:** Settings → **System Configuration** (platform parameters, maintained by tenant 1); Settings → **AI Configuration** (tenant-level preset/Key/RAG documents).

### 1.2 application.yml (Admin, fallback only)

```yaml
zestflow:
  ai:
    enabled: true                    # Global Copilot switch (sys_config: ai.enabled takes priority)
    default-preset: deepseek
    timeout-ms: 60000
    max-tokens: 4096
    tenant-auto-init: true           # Non-prod may auto-write default tenant AI config
    env-keys:                        # presetId → env var name (fallback Key, yaml only)
      deepseek: DEEPSEEK_API_KEY
      siliconflow: SILICONFLOW_API_KEY
      groq: GROQ_API_KEY
    rag-enabled: true
    rag-mode: hybrid                 # keyword | vector | hybrid
    rag-max-chunks: 3
    rag-tenant-data-dir: ./data/ai-rag
    rag-tenant-filesystem-enabled: true
    rag-tenant-max-documents: 200
    rag-tenant-max-content-bytes: 524288
```

On first startup, `SystemConfigSeeder` fills missing `sys_config` keys with current yaml values; thereafter UI edits take precedence.

### 1.3 Common Environment Variables

| Variable | Purpose |
|----------|---------|
| `DEEPSEEK_API_KEY` | DeepSeek preset fallback Key |
| `SILICONFLOW_API_KEY` | SiliconFlow preset fallback Key |
| `GROQ_API_KEY` | Groq preset fallback Key |
| `OPENAI_API_KEY` | OpenAI / compatible gateway |
| `ZESTFLOW_AI_ENABLED` | Override global Copilot switch (if deployment script supports it) |

**Principle:** In production, Keys are saved by tenants under Settings → AI Configuration; the platform does not host shared Keys. Set `tenant-auto-init` to false in production.

---

## 2. AI Provider Preset Maintenance

Provider enums are managed via dictionary `ai_provider` / `ai_model` (tree cascade). `ai-providers.yaml` is used **only on first startup with an empty database** as seed data; thereafter maintain via **Settings → Dictionary Management**.

### 2.1 Adding or Adjusting Presets

1. Add an entry under dictionary `ai_provider` (or edit in UI after yaml seed).
2. Maintain model list under the provider's `ai_model` child nodes.
3. The local `custom` preset allows tenants to enter any OpenAI-compatible Base URL.
4. When marked `deprecated: true`, specify `successor` to guide migration.
5. Changes take effect immediately; no Admin restart required.

### 2.2 Verification

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/zestflow/ai/providers
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"preset":"ollama","model":"qwen2.5:7b","apiKey":"ollama"}' \
  http://localhost:8080/api/zestflow/ai/test
```

---

## 3. Tenant RAG Knowledge Base (P5-A)

### 3.1 Data Sources

| Source | Description |
|--------|-------------|
| **Platform classpath** | `zestflow-admin/src/main/resources/ai-rag/*.md`, shared by all tenants |
| **Tenant DB** | Table `zf_ai_rag_document`, maintained via Admin UI or API |
| **Tenant file directory** | `{rag-tenant-data-dir}/{tenantId}/*.md`, optional |

At retrieval time, **platform + tenant** are merged, ranked by `hybrid` (TF-IDF + optional LLM Embedding); chunk count injected into the Prompt is controlled by `rag-max-chunks`.

### 3.2 Admin UI Operations

Path: **Settings → AI Configuration → Knowledge Base**

- Create/edit Markdown documents; optionally bind `appCode` (empty = tenant-wide).
- Disabled documents are excluded from indexing.
- **Rebuild index:** Click after document changes, or restart Admin (startup loads classpath + tenant index).

### 3.3 File Directory Mount (Optional)

```text
./data/ai-rag/
  1/                    # tenantId = 1
    order-rules.md
    aviator-cheatsheet.md
  2/
    ...
```

Requirements:

- `.md` files only; use `##` sections for chunking.
- Single file must not exceed `rag-tenant-max-content-bytes` (default 512KB).
- DB document count per tenant must not exceed `rag-tenant-max-documents` (default 200).
- After file changes, run **Rebuild index** or restart.

For Docker deployment, mount `./data/ai-rag` as a persistent volume.

### 3.4 API Quick Reference

| Method | Path | Permission |
|--------|------|------------|
| GET | `/api/ai/rag/status?appCode=` | Logged-in user; with `appCode` merges Executor-side knowledge base status |
| GET | `/api/ai/rag/search?q=&appCode=&limit=` | Logged-in user; prefers app-side `{dataDir}/ai/patterns` |
| POST | `/api/ai/executor/chains/suggest` | Logged-in user; proxies Executor RAG chain draft |
| GET | Executor `/api/ai/rag/status` | App-side local; MCP/Admin proxy access |
| POST | Executor `/api/ai/chains/suggest` | App-side local; pattern-based draft generation |
| GET | `/api/ai/rag/documents` | Logged-in user |
| POST/PUT/DELETE | `/api/ai/rag/documents` | Tenant administrator |
| POST | `/api/ai/rag/documents/rebuild-index` | Tenant administrator |

---

## 4. Usage and Audit Dashboard (P5-B)

Path: **Settings → AI Configuration → Usage Statistics**

Metrics come from `zf_ai_copilot_session` / `zf_ai_copilot_message`:

| Metric | Description |
|--------|-------------|
| Total sessions / success rate | Based on `success` field |
| Average latency | Mean of `latency_ms` (LLM calls; rule scaffold may be 0) |
| Token estimate | Sum of `token_estimate` in message table |
| Adoption rate | `adopted=1` / sessions with feedback |
| By mode / daily trend | explain, suggest, expression, diagnose, etc. |

API: `GET /api/ai/usage/overview?days=7|30|90` (tenant administrator)

**Privacy:** Message table stores only `content_summary`; full Prompts are not persisted.

---

## 5. Database Migration

Flyway `V1__init_admin_schema.sql` (Beta consolidation, includes former AI V3–V5):

- AI tables: `zf_ai_rag_document`, `zf_ai_tenant_config`, `zf_ai_copilot_session`, etc.
- `zf_ai_tenant_config` includes `monthly_token_quota` (monthly Token cap, NULL = unlimited)
- `zf_ai_copilot_session` includes `latency_ms`, `success`, `error_message`

On a new environment, Flyway runs V1 automatically after Admin starts; for existing beta databases, drop and recreate is recommended.

---

## 6. Troubleshooting

| Symptom | Investigation |
|---------|---------------|
| Copilot grayed out | Check `sys_config` `ai.enabled` (or yaml fallback), tenant "Enable Copilot", Key configured |
| RAG no hits | `/api/ai/rag/status` for `platformChunks` / `tenantChunks`; confirm documents `enabled=1` |
| File directory not working | `rag-tenant-filesystem-enabled=true`; path `{dir}/{tenantId}/*.md`; rebuild index |
| Usage empty | Requires Copilot sessions; some metrics are 0 for scaffold-only rule templates |
| Test connection fails | Verify preset, `baseUrl`, outbound network, Ollama listening |

E2E script: `scripts/blackbox/run-ai-copilot-e2e.ps1` (includes RAG and usage API probes).

---

## 7. Security Reminders

- Copilot **does not** auto save / publish / reload.
- LLM calls **only** from Admin; frontend does not contact third parties directly.
- When tenant RAG contains business knowledge, isolate by tenant; do not write sensitive Keys into Markdown.
- Disable `tenant-auto-init` in production to avoid accidental use of environment variable Keys.
