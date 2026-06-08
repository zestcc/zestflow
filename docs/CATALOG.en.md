# Complete Documentation Catalog

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](CATALOG.md) · [← Documentation hub](README.en.md)

This catalog lists **all** Markdown files under `docs/` with classification, audience, maintenance status, and available language versions.

---

## Statistics

| Category | Count | Description |
|----------|-------|-------------|
| User docs (Tutorial / How-to / Reference) | 17 | Includes API / annotations / engine / SPI / FAQ |
| Architecture & theory (Explanation) | 6 | Deep understanding |
| AI documentation | 8 | Copilot / MCP / IDE / learning / acceptance |
| Architecture Decision Records (ADR) | 2 | Immutable decision log |
| Release & handoff | 4 | Release, readiness, cross-machine continuation |
| Testing & acceptance | 5 | Black-box reports + acceptance |
| Meta documentation | 3 | Audit, maintenance, this catalog |
| **Total** | **35** | Excludes root README / CLAUDE.md; **English mirrors 35/35** |

All user-facing articles under `docs/` have a Chinese primary (`*.md`) and English mirror (`*.en.md`) with bidirectional `[English]` / `[简体中文]` links in the document header.

---

## I. User documentation

| File | Type | Audience | EN | Status |
|------|------|----------|----|--------|
| [GETTING_STARTED.md](GETTING_STARTED.md) · [EN](GETTING_STARTED.en.md) | Tutorial | New users | ✅ | ✅ 2026-06-08 |
| [MCP_SETUP.md](MCP_SETUP.md) | Tutorial | IDE developers | [EN](MCP_SETUP.en.md) | ✅ Indexed |
| [AI_IDE_SETUP.md](AI_IDE_SETUP.md) | Tutorial | Full IDE MCP comparison | [EN](AI_IDE_SETUP.en.md) | ✅ 2026-06-08 |
| [guides/COMPONENT_DEVELOPMENT.md](guides/COMPONENT_DEVELOPMENT.md) · [EN](guides/COMPONENT_DEVELOPMENT.en.md) | How-to | Business developers | ✅ | ✅ 2026-06-08 |
| [guides/CHAIN_ORCHESTRATION.md](guides/CHAIN_ORCHESTRATION.md) · [EN](guides/CHAIN_ORCHESTRATION.en.md) | How-to | Orchestration admins | ✅ | ✅ 2026-06-08 |
| [DEPLOY.md](DEPLOY.md) · [EN](DEPLOY.en.md) | How-to | Operations | ✅ | ✅ Indexed |
| [FLYWAY_POLICY.md](FLYWAY_POLICY.md) | How-to | DBA / backend | [EN](FLYWAY_POLICY.en.md) | ✅ Indexed |
| [QUICK_REFERENCE.md](QUICK_REFERENCE.md) | Reference | Developers | [EN](QUICK_REFERENCE.en.md) | ✅ Indexed |
| [reference/CONFIGURATION.md](reference/CONFIGURATION.md) | Reference | Configuration | [EN](reference/CONFIGURATION.en.md) | ✅ 2026-06-08 |
| [reference/API.md](reference/API.md) | Reference | REST/Netty API | [EN](reference/API.en.md) | ✅ 2026-06-08 |
| [reference/ANNOTATIONS.md](reference/ANNOTATIONS.md) | Reference | Full annotation set | [EN](reference/ANNOTATIONS.en.md) | ✅ 2026-06-08 |
| [reference/EXECUTION_ENGINE.md](reference/EXECUTION_ENGINE.md) | Reference | Execution engine API | [EN](reference/EXECUTION_ENGINE.en.md) | ✅ 2026-06-08 |
| [reference/SPI.md](reference/SPI.md) | Reference | Extension points | [EN](reference/SPI.en.md) | ✅ 2026-06-08 |
| [reference/OPENAPI.md](reference/OPENAPI.md) | Reference | OpenAPI 3 | [EN](reference/OPENAPI.en.md) | ✅ 2026-06-08 |
| [reference/FAQ.md](reference/FAQ.md) | Reference | FAQ | [EN](reference/FAQ.en.md) | ✅ 2026-06-08 |
| [openapi/README.md](openapi/README.md) · [EN](openapi/README.en.md) | Reference | Spec snapshot guide | ✅ | ✅ 2026-06-08 |
| [reference/GLOSSARY.md](reference/GLOSSARY.md) · [EN](reference/GLOSSARY.en.md) | Reference | Terminology | ✅ | ✅ 2026-06-08 |
| [../CHANGELOG.md](../CHANGELOG.md) | Reference | All users | [EN](../CHANGELOG.en.md) | ✅ Release sync |
| [../CONTRIBUTING.md](../CONTRIBUTING.md) · [EN](../CONTRIBUTING.en.md) | How-to | Contributors | ✅ | ✅ 2026-06-08 |
| [../README.md](../README.md) · [EN](../README.en.md) | Entry | All users | ✅ | ✅ Navigation updated |

---

## II. Architecture & theory

| File | Type | Description | EN | Status |
|------|------|-------------|----|--------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Explanation | C4 + modules + API matrix (~1800 lines) | [EN](ARCHITECTURE.en.md) | ✅ 2026-06-08 |
| [PROJECT_SUMMARY.md](PROJECT_SUMMARY.md) | Explanation | Engine and 28 component types overview | [EN](PROJECT_SUMMARY.en.md) | ✅ 2026-06-08 |
| [adr/SCHEDULING.md](adr/SCHEDULING.md) | ADR | Executor-autonomous Cron | [EN](adr/SCHEDULING.en.md) | ✅ Indexed |
| [adr/SCHEDULING_SPI_XXLJOB.md](adr/SCHEDULING_SPI_XXLJOB.md) | ADR | xxl-job SPI integration | [EN](adr/SCHEDULING_SPI_XXLJOB.en.md) | ✅ Indexed |

---

## III. AI documentation

| File | Type | Description | EN | Status |
|------|------|-------------|----|--------|
| [AI_COPILOT.md](AI_COPILOT.md) | Explanation + Reference | Admin orchestration Copilot (~1000 lines) | [EN](AI_COPILOT.en.md) | ✅ Implemented |
| [AI_COPILOT_OPS.md](AI_COPILOT_OPS.md) | How-to | AI ops, RAG, preset config | [EN](AI_COPILOT_OPS.en.md) | ✅ Implemented |
| [MCP_SETUP.md](MCP_SETUP.md) | Tutorial | Dev MCP install | [EN](MCP_SETUP.en.md) | ✅ Indexed |
| [AI_IDE_SETUP.md](AI_IDE_SETUP.md) | Tutorial | Full IDE MCP comparison | [EN](AI_IDE_SETUP.en.md) | ✅ 2026-06-08 |
| [AI_DEV_COPILOT_FINAL_SOLUTION.md](AI_DEV_COPILOT_FINAL_SOLUTION.md) | ADR / Design | Dev MCP final architecture | [EN](AI_DEV_COPILOT_FINAL_SOLUTION.en.md) | ✅ Phases 1–3 |
| [AI_DEV_COPILOT_ACADEMIC_SUMMARY.md](AI_DEV_COPILOT_ACADEMIC_SUMMARY.md) | Explanation | Academic background | [EN](AI_DEV_COPILOT_ACADEMIC_SUMMARY.en.md) | ✅ Theory |
| [AI_CHAIN_LEARNING.md](AI_CHAIN_LEARNING.md) | Explanation | Chain-first learning and RAG | [EN](AI_CHAIN_LEARNING.en.md) | ✅ P1–P3 |
| [AI_COPILOT_ACCEPTANCE.md](AI_COPILOT_ACCEPTANCE.md) | Acceptance | AI full-flow production | [EN](AI_COPILOT_ACCEPTANCE.en.md) | ✅ Automated |
| [HANDOFF-AI-EXECUTOR.md](HANDOFF-AI-EXECUTOR.md) | Handoff | Cross-machine AI/Executor KB | [EN](HANDOFF-AI-EXECUTOR.en.md) | ✅ Maintainer |

**AI reading path:** `AI_COPILOT.md` → `MCP_SETUP.md` → `AI_CHAIN_LEARNING.md` → acceptance docs

---

## IV. Release, handoff & meta documentation

| File | Description | EN | Status |
|------|-------------|----|--------|
| [RELEASE_READINESS.md](RELEASE_READINESS.md) | Open-source three-tier gates | [EN](RELEASE_READINESS.en.md) | ✅ |
| [PUBLISH_HANDOFF.md](PUBLISH_HANDOFF.md) | Maven Central release handoff | [EN](PUBLISH_HANDOFF.en.md) | ✅ |
| [AUDIT_REPORT.md](AUDIT_REPORT.md) | Code audit + doc quality | [EN](AUDIT_REPORT.en.md) | ✅ 2026-06-08 |
| [DOCUMENTATION_MAINTENANCE.md](DOCUMENTATION_MAINTENANCE.md) · [EN](DOCUMENTATION_MAINTENANCE.en.md) | Doc version control + PR checklist | ✅ | ✅ 2026-06-08 |
| [CATALOG.md](CATALOG.md) · [EN](CATALOG.en.md) | This catalog | ✅ | ✅ 2026-06-08 |

---

## V. Testing & acceptance

| File | Description | Automation | EN |
|------|-------------|------------|-----|
| [FULL_E2E_TEST_REPORT.md](FULL_E2E_TEST_REPORT.md) | Full E2E report template | `run-full-e2e.ps1` | [EN](FULL_E2E_TEST_REPORT.en.md) |
| [BLACKBOX_TEST_REPORT.md](BLACKBOX_TEST_REPORT.md) | Black-box smoke + load | `run-blackbox.ps1` | [EN](BLACKBOX_TEST_REPORT.en.md) |
| [acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.md) | Executor AI v2 | Manual + unit tests | [EN](acceptance/AI_EXECUTOR_V2_ACCEPTANCE.en.md) |
| [acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md) | Scheduling / SLA / registry | `run-scheduling-registry-sla-e2e.ps1` | [EN](acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.en.md) |
| [AI_COPILOT_ACCEPTANCE.md](AI_COPILOT_ACCEPTANCE.md) | AI Copilot full-flow | `run-ai-copilot-acceptance.ps1` | [EN](AI_COPILOT_ACCEPTANCE.en.md) |

---

## VI. Related documentation outside `docs/`

| Path | Description |
|------|-------------|
| [../CLAUDE.md](../CLAUDE.md) | AI collaboration dev spec (not user docs) |
| [../zestflow-admin/src/main/resources/db/migration/README.md](../zestflow-admin/src/main/resources/db/migration/README.md) | Admin Flyway migration guide |
| [../scripts/blackbox/README.md](../scripts/blackbox/README.md) | Black-box script usage |
| [../scripts/dev/mcp/README.md](../scripts/dev/mcp/README.md) | MCP install script guide |

---

## VII. Topics not yet standalone (optional future)

| Topic | Current coverage | Suggestion |
|-------|------------------|------------|
| OpenAPI REST reference | ✅ `springdoc` + `docs/openapi/admin-api.json` | Run `export-openapi.ps1` before release |
| Bilingual documentation | ✅ 35/35 user docs + CHANGELOG/CONTRIBUTING | Sync `*.en.md` when editing Chinese |
| Multi-tenant operations | DEPLOY + RELEASE_READINESS | Could merge into one How-to |

---

## Maintenance notes

- When adding `docs/**/*.md`, update **this catalog**, [CATALOG.md](CATALOG.md), and [README.en.md](README.en.md) / [README.md](README.md)
- Long-form docs are complete; priority is **indexing + unified navigation**, not rewriting existing quality content
- New user-facing Chinese docs should get English mirrors (`*.en.md`) with bidirectional language links
