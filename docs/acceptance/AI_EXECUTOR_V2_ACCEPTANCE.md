# Executor AI v2 生产验收清单

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** 验收 · [← 文档中心](../README.md)  
> 2026-06-08 · LLM suggest + Hybrid RAG + CONDITION 布局

## 1. Executor LLM suggest

| # | 场景 | 预期 |
|---|------|------|
| 1 | `llm-enabled=false` | `/api/ai/chains/suggest` 回落 pattern 抽取，`source=executor-pattern:*` |
| 2 | `llm-enabled=true` + Ollama/OpenAI | `source=executor-llm`，含 `summary` + `proposedChainData` |
| 3 | 用户注册类需求 | 质量门禁拒绝单节点黑盒，自动重试后 ≥3 业务节点 + CONDITION |
| 4 | validate 失败 | `repair-max-rounds` 内 LLM 修复，`validation.valid=true` |
| 5 | LLM 不可用 | `pattern-fallback-enabled=true` 时不 500，回落 pattern |

配置：`zestflow.executor.ai.*`（见 `zestflow-executor/application.yml`）

## 2. Hybrid RAG

| # | 场景 | 预期 |
|---|------|------|
| 1 | `rag-mode=keyword` | 纯关键词命中 |
| 2 | `rag-mode=hybrid` | TF-IDF + 关键词加权，蒸馏 pattern 优先 |
| 3 | `rag-use-embedding=true` + LLM | Top-K 候选 embedding 重排（Ollama nomic-embed-text） |
| 4 | 高置信学习事件 | 自动蒸馏后 `searchRag` 可检索 |

## 3. CONDITION 布局

| # | 场景 | 预期 |
|---|------|------|
| 1 | Copilot 采纳链到画布 | True 分支在左、False 在右（BPMN 读法） |
| 2 | 多 CONDITION 链 | 各条件节点分支独立偏移，不重叠 |
| 3 | 单元测试 | `npm run test:unit` chainApply.spec.ts 全绿 |

## 4. 自动化测试

```bash
mvn test -pl zestflow-executor -Dtest=Executor*Ai*,ExecutorChain*
cd zestflow-admin-ui && npm install && npm run test:unit
```

## 5. 对标方案

| 能力 | 参考 |
|------|------|
| LLM suggest + validate 闭环 | Admin AiCopilotService |
| Hybrid RAG | LangChain Ensemble Retriever / Admin AiRagIndexEngine |
| 分支布局 | Camunda BPMN True/False 分列 |
