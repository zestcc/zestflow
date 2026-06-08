# ZestFlow AI End-to-End Production Acceptance Specification

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](AI_COPILOT_ACCEPTANCE.md) · **Type** Acceptance · [← Documentation hub](README.en.md)

| Item | Content |
|------|---------|
| Version | 1.0 |
| Updated | 2026-06-02 |
| Scope | **Orchestration Copilot** (Admin) + **Dev Copilot** (`zestflow-mcp`) |
| Related design | [AI_COPILOT.en.md](./AI_COPILOT.en.md) · [AI_DEV_COPILOT_FINAL_SOLUTION.en.md](./AI_DEV_COPILOT_FINAL_SOLUTION.en.md) · [MCP_SETUP.en.md](./MCP_SETUP.en.md) |
| Automation entry | `scripts/blackbox/run-ai-copilot-acceptance.ps1` |

---

## 1. Acceptance Model

```text
Layer U  Unit tests (Mock LLM / no network)     → mvn test
Layer I  Integration tests (Controller / MCP)     → mvn test
Layer B  API black-box (Admin + Executor)       → run-ai-copilot-e2e.ps1
Layer M  Dev MCP black-box (CLI + Executor)     → run-ai-mcp-e2e.ps1
Layer P  Performance / load (no LLM + optional Mock) → run-ai-copilot-perf.ps1
Layer M  Manual UI walkthrough (designer / settings) → §6 manual cases in this doc
```

**Release gate recommendations**

| Environment | Required runs |
|-------------|---------------|
| CI (no LLM) | Layer U + I; black-box with `-AllowLlmSkip` |
| Pre-release | All layers + `-RequireLlm -UseMockLlm` |
| Pre-production | All layers + real Ollama/DeepSeek sample (3 LLM cases) |

**Component AI note:** Admin component page "AI scaffold" **has been removed**; component creation acceptance belongs to **Dev Copilot** (MCP `scaffold_component` + IDE Apply), see §4.

---

## 2. Environment and Prerequisites

| Item | Requirement |
|------|-------------|
| JDK | 17+, `JAVA_HOME` points to root containing `bin\java.exe` |
| Admin | `:8080`, `admin/admin123` |
| Demo Executor | `:20550`, `app-code=demo-app` |
| MySQL | `scripts/init.ps1` + `initData.ps1` executed |
| MCP JAR | `powershell -File scripts/dev/install-mcp.ps1` (required for Layer M, installed to `~/.zestflow/tools/`) |
| LLM (optional) | Ollama `qwen2.5:7b` or `-UseMockLlm` |

---

## 3. Automated Script Mapping

| Script | Layer | Description |
|--------|-------|-------------|
| `mvn test -pl zestflow-admin -Dtest=Ai*Test` | U/I | Admin AI unit tests |
| `mvn test -pl zestflow-mcp -am` | U/I | MCP unit tests |
| `run-ai-copilot-e2e.ps1` | B | Full Admin Copilot API |
| `run-ai-mcp-e2e.ps1` | M | MCP CLI + Executor alignment |
| `run-ai-copilot-perf.ps1` | P | validate/RAG concurrent load |
| `run-ai-copilot-acceptance.ps1` | Aggregate | One-click gate for above |

Report output: `scripts/blackbox/results/ai-*.json` (local, not committed).

---

## 4. Dev Copilot — Component AI Creation (MCP)

> **Production path:** Open `zestflow-demo` in Cursor → MCP Tools → IDE Apply to write files.  
> **Forbidden:** Admin `POST /ai/component/scaffold` (removed).

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-MCP-001 | P0 | U | Scaffold generation | `ComponentScaffoldGenerator.scaffold(...)` | Contains `@ZestExecute`, `suggestedRelativePath`, Apply hint | `ComponentScaffoldGeneratorTest` |
| TC-MCP-002 | P0 | U | Source search | `ProjectSourceSearcher.search` keyword `@ZestComponent` | Returns JSON hit list | `ProjectSourceSearcherTest` |
| TC-MCP-003 | P0 | U | Spec resource load | `ResourceLoader` reads classpath MD | Non-empty, UTF-8 | `ResourceLoaderTest` |
| TC-MCP-004 | P0 | U | CLI arg parsing | `--export-task-package -o file.md` | Does not start stdio MCP | `McpRuntimeConfigParserTest` |
| TC-MCP-005 | P0 | B | Task package CLI export | `java -jar ... --export-task-package --project zestflow-demo ...` | Markdown contains spec summary, component whitelist, `project.md` | `run-ai-mcp-e2e.ps1` → `mcp-cli-export` |
| TC-MCP-006 | P0 | B | Component whitelist | MCP equivalent HTTP `GET :20550/api/components` | 200, includes demo components like `validateUser` | `mcp-executor-list-components` |
| TC-MCP-007 | P0 | B | Chain validate (valid) | Submit ChainDefinition with `validateUser` | `valid=true` | `mcp-executor-validate-valid` |
| TC-MCP-008 | P0 | B | Chain validate (invalid componentId) | Submit `component=__NOT_REGISTERED__` | `valid=false`, includes error message | `mcp-executor-validate-invalid` |
| TC-MCP-009 | P1 | B | demo MCP config | Check `zestflow-demo/.cursor/mcp.json` | `${userHome}/.zestflow/tools/` + `workspaceFolder`, `app-code=demo-app` | `mcp-demo-cursor-config` |
| TC-MCP-010 | P1 | B | Default build includes MCP | Root `pom.xml` `<modules>` | **Includes** `zestflow-mcp` (IDE recognition; `mvn package` produces fat JAR) | Manual / CI pom review |
| TC-MCP-011 | P1 | B | JAR not in demo package | `package-demo.ps1` artifact | No MCP JAR inside Spring Boot | Manual unzip acceptance |
| TC-MCP-012 | P2 | M | Cursor E2E | Agent calls `list_components` → `scaffold_component` → Apply | Java file at expected package path; `mvn compile` passes | Manual |
| TC-MCP-013 | P2 | M | Audit log | After any Tool call | `.zestflow/mcp-audit.jsonl` appends one line | Manual |
| TC-MCP-014 | P2 | B | Path traversal protection | `read_project_file` with `../../etc/passwd` | Rejected / error, no leak | Manual + extended unit test |

### 4.1 Chain-first Learning and Retention (P1–P3)

> Design details in [AI_CHAIN_LEARNING.en.md](./AI_CHAIN_LEARNING.en.md). Platform Patterns (L0) in MCP JAR; project Patterns (L2) in `.zestflow/patterns/`; tenant RAG (L1) via Admin promotion.

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-LRN-001 | P0 | U | Promotion threshold | `AccuracyGate.evaluate` high-confidence event | `passed=true`, score≥0.97 | `AccuracyGateTest` |
| TC-LRN-002 | P0 | U | Validate failure rejected | validatePassed=false | `passed=false` | `AccuracyGateTest` |
| TC-LRN-003 | P0 | U | Registration chain plan | `ChainPlanService.plan("registration chain")` | feature=userRegister, includes gap/reuse | `ChainPlanServiceTest` |
| TC-LRN-004 | P0 | B | Platform Pattern packaging | MCP JAR `zestflow/patterns/platform/` | Contains index.json, http-three-mode | `run-ai-mcp-e2e.ps1` → `mcp-platform-patterns` |
| TC-LRN-005 | P0 | B | Project learning directory | `zestflow-demo/.zestflow/learning/` | `.gitignore` ignores events.jsonl | `run-ai-mcp-e2e.ps1` → `mcp-demo-learning-dir` |
| TC-LRN-006 | P0 | B | Admin record event | `POST /ai/learning/events` | 200, includes promotionScore | `run-ai-copilot-e2e.ps1` → `ai-learning-events-save` |
| TC-LRN-007 | P0 | B | Admin list | `GET /ai/learning/events?appCode=demo-app` | Contains newly written event | `ai-learning-events-list` |
| TC-LRN-008 | P0 | B | Low score cannot promote | `POST .../promote-rag` on below-threshold event | 4xx validation | `ai-learning-promote-rejected` |
| TC-LRN-009 | P0 | B | High score promote to RAG | promote-rag on high-confidence event | Returns RAG document id | `ai-learning-promote-rag` |
| TC-LRN-010 | P1 | M | MCP plan_chain | Cursor Agent "develop registration chain" | Calls plan_chain + validate_chain | Manual |
| TC-LRN-011 | P1 | M | Distill retention | record → distill_patterns | `.zestflow/patterns/*.md` updated | Manual |
| TC-LRN-012 | P1 | M | Team sharing | share_pattern → RAG import | Tenant search hits | Manual |

---

## 5. Orchestration Copilot — Admin AI Chain Building

### 5.1 Platform Configuration and Switches

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-001 | P0 | B | Global Copilot switch | `GET /system/features` | `copilot.globallyEnabled=true` (playground env) | `copilot-globally-enabled` |
| TC-ADM-002 | P0 | B | AI config readable | `GET /ai/config` | 200, includes enabled, preset info | `ai-config` |
| TC-ADM-003 | P0 | B | Preset list | `GET /ai/providers` | ≥20 presets | `ai-providers-list` |
| TC-ADM-004 | P0 | B | Tenant AI save | `PUT /ai/tenant-config` | 200 | `ai-tenant-config-save` |
| TC-ADM-005 | P0 | B | Tenant AI read | `GET /ai/tenant-config` | Includes enabled, preset | `ai-settings-tab-config-api` |
| TC-ADM-006 | P0 | B | Connection test | `POST /ai/test` (Mock LLM) | success=true | `ai-test-connection` |
| TC-ADM-007 | P1 | U | Copilot disabled intercept | explain when `isCopilotEnabled=false` | Throws `AI_COPILOT_DISABLED` | `AiCopilotServiceTest` |
| TC-ADM-008 | P1 | M | No Key grayed out | Tenant enabled but no Key | UI Copilot entry disabled + settings guidance | Manual |

### 5.2 Designer Context

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-010 | P0 | B | Component whitelist context | `GET /ai/context/components?appCode=demo-app` | JSON contains registered componentId | `ai-context-components` |
| TC-ADM-011 | P0 | B | chainCtx key hints | `GET /ai/context/chain-keys?appCode=demo-app` | declaredKeys + adminKeys non-empty | `ai-chain-key-hints` |

### 5.3 Chain Validation (No LLM)

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-020 | P0 | B | Valid chain validate | `POST /ai/design/validate` with `validateUser` | valid field present | `ai-validate-chain` |
| TC-ADM-021 | P0 | B | Invalid componentId | validate with unregistered component | `valid=false` | `ai-validate-invalid-component` |
| TC-ADM-022 | P0 | U | repair loop | suggest returns invalid JSON → second round valid | `repairRounds=1`, final valid | `AiCopilotServiceTest.suggest_shouldRepairUntilValid` |

### 5.4 Chain Building (LLM)

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-030 | P0 | B | Explain chain | `POST /ai/design/explain` | `explanation` non-empty, ≤120s | `ai-design-explain` |
| TC-ADM-031 | P0 | B | Generate chain draft | `POST /ai/design/suggest` mode=generate | `proposedChainData` + `validation` | `ai-design-suggest` |
| TC-ADM-032 | P1 | B | Refine chain draft | suggest mode=refine + existing chainData | Returns diff-level proposal | Manual / extended e2e |
| TC-ADM-033 | P0 | M | Apply to canvas | UI "Apply to canvas" | Canvas updated; **not** auto saved/published | Manual |
| TC-ADM-034 | P0 | M | Undo apply | UI undo | Canvas rolled back | Manual |
| TC-ADM-035 | P0 | M | Invalid proposal cannot publish | Publish when validation.valid=false | Blocked by Validator | Manual |
| TC-ADM-036 | P1 | U | Markdown fence parsing | LLM returns ` ```json ` wrapped | Correctly parses chainData | `parseChainProposal` unit test |

**Chain building SLA (MVP):** From user message to validatable draft ≤ **60 seconds** (Mock LLM / local Ollama, P95).

### 5.5 Expression Assistant

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-040 | P0 | B | Expression suggest | `POST /ai/expression/suggest` | `expression` non-empty | `ai-expression-suggest` |
| TC-ADM-041 | P1 | M | Designer expression panel | Select node → Copilot expression Tab | Can insert suggested expression | Manual |

### 5.6 Log Diagnosis

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-050 | P0 | B | Diagnose API | `POST /ai/logs/diagnose` | `diagnosis` non-empty | `ai-logs-diagnose` |
| TC-ADM-051 | P0 | U | Trace + LLM | executionId + Collector trace | Non-stub, includes node names | `AiCopilotServiceTest.diagnose_shouldUseTraceAndLlm` |
| TC-ADM-052 | P1 | M | Jump to designer | Diagnosis result link | Opens corresponding design/chain | Manual |

### 5.7 Template Library

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-060 | P0 | B | List | `GET /ai/templates?appCode=demo-app` | 200 | `ai-templates-list` |
| TC-ADM-061 | P0 | B | Save | `POST /ai/templates` | Returns id | `ai-templates-save` |
| TC-ADM-062 | P0 | B | Detail | `GET /ai/templates/{id}` | Matches saved | `ai-templates-get` |
| TC-ADM-063 | P0 | B | Delete | `DELETE /ai/templates/{id}` | 200 | `ai-templates-delete` |

### 5.8 RAG

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-070 | P0 | B | Platform search | `GET /ai/rag/search?q=Aviator` | hits>0 | `ai-rag-search` |
| TC-ADM-071 | P0 | B | Vector mode | `GET /ai/rag/status` | mode=vector/hybrid* | `ai-rag-vector-mode` |
| TC-ADM-072 | P0 | B | Tenant document CRUD | POST/PUT/DELETE documents | Success | `ai-rag-documents-*` |
| TC-ADM-073 | P0 | B | Rebuild index | POST rebuild-index | 200 | `ai-rag-rebuild-index` |
| TC-ADM-074 | P0 | B | Tenant search | search after rebuild | Hits new document | `ai-rag-tenant-search-after-rebuild` |
| TC-ADM-075 | P1 | B | Import/export | export + import | imported>0 | `ai-rag-documents-export/import` |
| TC-ADM-076 | P1 | U | RAG service | hybrid search ranking | Relevant chunks first | `AiRagServiceTest` |

### 5.9 Usage and Quota

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-080 | P0 | B | Usage overview | `GET /ai/usage/overview?days=30` | totalSessions, sessionsByMode | `ai-usage-overview` |
| TC-ADM-081 | P1 | B | Quota fields | Same as above | monthlyTokenUsed present | `ai-usage-quota-fields` |

### 5.10 Security and Multi-tenancy

| ID | Priority | Type | Scenario | Steps | Expected | Automation |
|----|----------|------|----------|-------|----------|------------|
| TC-ADM-100 | P0 | B | No JWT | Access `/ai/design/suggest` | 401/403 | `run-rbac-horizontal-e2e.ps1` |
| TC-ADM-101 | P0 | M | Tenant isolation | multi mode tenants A/B | Tenant A AI sessions invisible to B | `run-tenant-multi-e2e.ps1` + manual |
| TC-ADM-102 | P0 | M | Key not in logs | Run explain with debug logging | Logs contain no apiKey plaintext | Manual grep |
| TC-ADM-103 | P0 | M | AI cannot reload | No reload in Copilot output; no corresponding API call | No `PUT /reload` | Manual + architecture review |

---

## 6. Manual UI Walkthrough Checklist

| ID | Page | Action | Expected |
|----|------|--------|----------|
| TC-UI-001 | Designer | Open Copilot Drawer | Explain / Suggest / Validate Tabs available |
| TC-UI-002 | Designer | Suggest → preview diff | Proposal only, no auto save |
| TC-UI-003 | Designer | Apply to canvas | Undo available; save still manual |
| TC-UI-004 | Designer | Expression Copilot | Suggestion insertable into node expression |
| TC-UI-005 | Logs page | AI diagnosis | Opens Drawer, shows diagnosis |
| TC-UI-006 | Settings → AI | Four Tabs (config/providers/RAG/usage) | Matches API data; **no** "Dev Assistant (MCP)" |
| TC-UI-007 | Components page | No "AI scaffold" button | **Removed** |
| TC-UI-008 | Playground | Copilot unavailable or read-only hint | Consistent with product policy |

---

## 7. Performance and Load Testing

| ID | Metric | Threshold (playground env reference) | Script |
|----|--------|--------------------------------------|--------|
| TC-PERF-001 | `POST /ai/design/validate` 20×50 concurrent | Error rate 0%; P95 < 2s | `run-ai-copilot-perf.ps1` |
| TC-PERF-002 | `GET /ai/rag/search` 10×30 concurrent | Error rate 0%; P95 < 3s | Same |
| TC-PERF-003 | Mock LLM explain 5×10 concurrent | Error rate 0%; P95 < 5s | Same `-UseMockLlm` |
| TC-PERF-004 | MCP CLI export 10 consecutive runs | All succeed; each < 10s | `run-ai-mcp-e2e.ps1` |

---

## 8. Regression Commands (Copy Before Release)

```powershell
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"   # Adjust for your machine
cd D:\project\2\zestflow

# 1. MCP JAR (Layer M prerequisite)
powershell -File scripts/dev/install-mcp.ps1

# 2. Start Admin:8080 + demo:20550, then:

# 3. Full AI acceptance (unit + black-box + MCP + load)
powershell -File scripts/blackbox/run-ai-copilot-acceptance.ps1 -RequireLlm -UseMockLlm

# 4. Black-box only (when no LLM)
powershell -File scripts/blackbox/run-ai-copilot-e2e.ps1 -AllowLlmSkip
powershell -File scripts/blackbox/run-ai-mcp-e2e.ps1
```

**Pass criteria:** Each script exit 0; `results/ai-*.json` has zero `ok=false` entries; manual §6 fully checked.

---

## 9. Change Log

| Date | Change |
|------|--------|
| 2026-06-02 | Initial: dual Copilot acceptance matrix; Admin scaffold removed; MCP component creation under Layer M |
| 2026-06-02 | Added P1–P3 learning acceptance TC-LRN-001–012; 12 MCP Tools |

---

*This document is the sole acceptance baseline for AI features in production; new APIs or Tools must add TC IDs and update black-box scripts.*
