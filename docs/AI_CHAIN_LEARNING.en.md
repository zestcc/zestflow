# Chain-first AI Learning and Knowledge Retention (P1–P3)

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](AI_CHAIN_LEARNING.md) · **Type** Explanation · [← Documentation hub](README.en.md)  
> **Goal:** Intent-driven + improves with use (Learning RAG) + platform/project layering + promotion threshold **≥97%**  
> **Benchmarks:** LangSmith Feedback, Mem0 curation, Cursor Rules layering, Stripe MCP spec binding

---

## 1. Knowledge Layering

| Layer | Storage | Sharing |
|-------|---------|---------|
| **L0 Platform** | MCP JAR `zestflow/patterns/platform/` | Read-only for all; shipped with releases |
| **L1 Team** | Admin RAG documents (promoted via import) | Tenant export/import |
| **L2 Project** | `.zestflow/patterns/` + `rules/project.md` | Git inheritance |
| **L3 Raw signals** | `.zestflow/learning/events.jsonl` + `zf_ai_learning_event` | Not fed directly to LLM |

---

## 2. Intent Workflow

```text
plan_chain → scaffold_component(gap) → compose/validate → bind_http(Mode1/2/3)
  → gen_playground_scene → record_learning_event → distill_patterns → share_pattern
```

| User keywords | MCP Tool |
|---------------|----------|
| Develop/plan … chain | `plan_chain` |
| Generate component | `scaffold_component` |
| Compose chain / validate | `validate_chain` |
| Mode1/2/3 | Read Pattern `platform-http-three-mode` |
| Generate scene | `gen_playground_scene` |
| Feedback / adoption | `record_learning_event` |
| Distill and retain | `distill_patterns` |
| Team sharing | `share_pattern` → Admin RAG import |

---

## 3. P1 — Learning Events

**Dev (MCP):** `record_learning_event` → `.zestflow/learning/events.jsonl`

**Admin (Orchestration):**

- `POST /api/zestflow/ai/learning/events`
- `POST /api/zestflow/ai/sessions/{id}/feedback` (extended intent/feature/validate fields)

---

## 4. P2 — Distillation and Retrieval

- `distill_patterns`: high-confidence events → `.zestflow/patterns/*.md` + `index.json`
- `search_patterns` / `plan_chain` automatically retrieve **platform + project** Patterns
- **Promotion criteria (AccuracyGate ≥0.97):**
  - `validatePassed=true`
  - `validateRounds ≤ 2`
  - `adopted` or `playgroundSuccess`
  - Includes intent + feature

---

## 5. P3 — Inheritance and Sharing

| Method | Operation |
|--------|-----------|
| Git | Commit `.zestflow/patterns/`, `rules/project.md` |
| MCP | `share_pattern` → JSON → Admin `POST /ai/rag/documents/import` |
| Admin | `POST /ai/learning/events/{id}/promote-rag` (tenant administrator) |

---

## 6. 97% Accuracy Explained

**This is not** LLM self-rating at 97%, but rather:

1. **Structured plan** (component types, I/O, gap comparison) reduces hallucination  
2. **Validator hard gate** — failure does not count as completion  
3. **Promotion curation** — only high-confidence samples enter Pattern/RAG  
4. **Human confirmation** — plan / Mode / publish remain manual  

Measured metric: promoted samples should achieve **≥97% first-pass validate rate** on the same feature template (verified via E2E + promotion sample statistics).

---

## 7. Quick Start

```powershell
# Platform JAR (once per machine)
powershell -File scripts/dev/install-mcp.ps1

# Business project Dev files (once per project)
powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot .

# Open business project in Cursor, then chat:
# "Help me develop a registration chain" → Agent should call plan_chain
# After completion: record_learning_event → distill_patterns
```

`--init-dev` generates `.zestflow/rules/project.md`, `.cursor/mcp.json`, `.zestflow/learning/`. See [AI_COPILOT.en.md §1.6](./AI_COPILOT.en.md#16-dev-project-setup-platform-jar--init-dev).

---

*See [AI_COPILOT_ACCEPTANCE.en.md](./AI_COPILOT_ACCEPTANCE.en.md) for acceptance test cases.*
