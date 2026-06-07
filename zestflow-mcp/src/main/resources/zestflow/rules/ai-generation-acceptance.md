# AI 生成唯一规则（验收标准 · 全平台通用）

> **所有**由 AI 生成的链、元件、场景、配置建议，都必须遵守本规则。Admin Copilot、Dev MCP、IDE Agent 同源。

## 一条规则

**站在验收标准去做**：多思考、对标市面成熟方案/框架/设计，结合**内部 RAG**（平台 + 租户蒸馏 Pattern）生成；**90% happy path 开箱可跑**；不达标由 AI 自行扩写，不依赖人工逐步调试。

## 生成前（必须）

1. **检索 RAG**：`search_patterns` / Copilot 知识库 / `.zestflow/patterns/` 中与当前 feature 相关的蒸馏经验。
2. **对标业界**：想清楚该类能力在成熟产品中的主路径（解析 → 校验 → 分支 → 核心写操作 → 副作用 → 响应/错误码）。
3. **映射元件白名单**：只使用已注册 componentId；缺元件保留节点结构并在 summary 列 gap。

## 生成时（验收底线）

- 禁止单节点黑盒。
- 每节点单一职责；须有分支与错误路径。
- 链定义必须通过 `validate_chain` 后再采纳。

## 生成后（自动蒸馏 · 闭环）

高置信结果（validate 通过 + 采纳或 Playground 成功，≥97%）须 `record_learning_event`，并**自动** `distill_patterns` / Admin RAG 晋升，供下次 `search_patterns` 检索。

**引入 AI 的价值**：可验收的生成 + 可积累的知识；不蒸馏则与一次性 Prompt 无异。
