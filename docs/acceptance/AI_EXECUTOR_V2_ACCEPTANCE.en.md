# Executor AI v2 Production Acceptance Checklist

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](AI_EXECUTOR_V2_ACCEPTANCE.md) · **Type** Acceptance · [← Documentation hub](../README.en.md)  
> 2026-06-08 · LLM suggest + Hybrid RAG + CONDITION layout

## 1. Executor LLM suggest

| # | Scenario | Expected |
|---|----------|----------|
| 1 | `llm-enabled=false` | `/api/ai/chains/suggest` falls back to pattern extraction, `source=executor-pattern:*` |
| 2 | `llm-enabled=true` + Ollama/OpenAI | `source=executor-llm`, includes `summary` + `proposedChainData` |
| 3 | User registration requirement | Quality gate rejects single-node black box; auto-retry yields ≥3 business nodes + CONDITION |
| 4 | validate failure | LLM repair within `repair-max-rounds`, `validation.valid=true` |
| 5 | LLM unavailable | With `pattern-fallback-enabled=true`, no 500, falls back to pattern |

Configuration: `zestflow.executor.ai.*` (see `zestflow-executor/application.yml`)

## 2. Hybrid RAG

| # | Scenario | Expected |
|---|----------|----------|
| 1 | `rag-mode=keyword` | Pure keyword hits |
| 2 | `rag-mode=hybrid` | TF-IDF + keyword weighting; distilled patterns prioritized |
| 3 | `rag-use-embedding=true` + LLM | Top-K candidate embedding rerank (Ollama nomic-embed-text) |
| 4 | High-confidence learning event | After auto-distill, `searchRag` retrievable |

## 3. CONDITION Layout

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Copilot adopted chain to canvas | True branch left, False right (BPMN reading) |
| 2 | Multi-CONDITION chain | Each condition node branches offset independently, no overlap |
| 3 | Unit test | `npm run test:unit` chainApply.spec.ts all green |

## 4. Automated Tests

```bash
mvn test -pl zestflow-executor -Dtest=Executor*Ai*,ExecutorChain*
cd zestflow-admin-ui && npm install && npm run test:unit
```

## 5. Benchmark Solutions

| Capability | Reference |
|------------|-----------|
| LLM suggest + validate closed loop | Admin AiCopilotService |
| Hybrid RAG | LangChain Ensemble Retriever / Admin AiRagIndexEngine |
| Branch layout | Camunda BPMN True/False columns |
