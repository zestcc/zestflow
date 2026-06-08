# Acceptance Test Documentation Index

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](README.md) · [← Documentation hub](../README.en.md)

This directory contains production-grade acceptance criteria and test matrices for ZestFlow subsystems. Each document defines repeatable scenarios with expected outcomes and links to automated test scripts where available.

---

## Documents

### [AI_EXECUTOR_V2_ACCEPTANCE.md](AI_EXECUTOR_V2_ACCEPTANCE.en.md)

Production acceptance checklist for **Executor AI v2** (2026-06-08), covering LLM-based chain suggestion with pattern fallback, Hybrid RAG (keyword / TF-IDF / embedding rerank), and CONDITION node layout on the design canvas (BPMN-style True-left / False-right). Includes unit test commands for `zestflow-executor` and `zestflow-admin-ui`.

### [SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](SCHEDULING_SLA_REGISTRY_ACCEPTANCE.en.md)

Production acceptance matrix for **scheduling, SLA alerting, registry lifecycle, and xxl-job integration**. Covers embedded chain Cron (`EmbeddedScheduleDriver`), SLA scanning moved to Collector, event-driven offline detection (replacing Admin polling), and `ExternalScheduleDriver` with xxl-job handlers. Includes black-box script IDs (`run-scheduling-registry-sla-e2e.ps1`, `run-enterprise-gate.ps1`, `run-perf-gate.ps1`) and layered testing principles (L1 unit → L4 performance gates).

---

## Running acceptance tests

| Area | Command |
|------|---------|
| AI Copilot (Admin) | `powershell -File scripts/blackbox/run-ai-copilot-acceptance.ps1 -UseMockLlm` |
| Scheduling / SLA / Registry | `powershell -File scripts/blackbox/run-scheduling-registry-sla-e2e.ps1` |
| Enterprise release gate | `powershell -File scripts/blackbox/run-enterprise-gate.ps1` |

See each acceptance document for scenario IDs, prerequisites, and expected results.
