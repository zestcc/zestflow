# AI 生成唯一规则（验收标准 · 全平台通用）

> **所有**由 AI 生成的链、元件、场景、配置建议，都必须遵守本规则。Admin Copilot、Dev MCP、IDE Agent 同源。

## 一条规则

**站在验收标准去做**：多思考、对标市面成熟方案/框架/设计，结合**内部 RAG**（平台 + 租户蒸馏 Pattern）生成；**90% happy path 开箱可跑**；不达标由 AI 自行扩写，不依赖人工逐步调试。

## 生成前（必须）

1. **检索 RAG**：`search_patterns` / Copilot 知识库 / `.zestflow/patterns/` 中与当前 feature 相关的蒸馏经验。
2. **对标业界**：想清楚该类能力在成熟产品中的主路径（解析 → 校验 → 分支 → 核心写操作 → 副作用 → 响应/错误码）。
3. **映射元件白名单**：只使用已注册 componentId；缺元件保留节点结构并在 summary 列 gap。

## 生成时（验收底线）

- 禁止单节点黑盒（如「开始 → 用户注册 → 结束」）。
- 每节点单一职责；label 用动词短语；`description` 写明 chainCtx 读写。
- 业务判断须有 CONDITION 分叉与失败/异常路径。
- 链定义必须通过 `validate_chain` / Executor 校验后再采纳。

## 生成后（自动蒸馏 · 闭环）

高置信结果（校验通过 + 采纳或 Playground 成功，score ≥97%）须：

1. `record_learning_event`（Admin 采纳反馈 / MCP 工作流结束）
2. **自动晋升 RAG**（Admin 租户文档）或 **`distill_patterns`**（项目 `.zestflow/patterns/`）
3. 后续同类需求**优先检索**上述蒸馏结果，避免重复从零敷衍

**引入 AI 的价值**：不是少写几个字，而是**可验收的生成 + 可积累的知识**；不蒸馏 RAG 则与一次性 Prompt 无异。

## 设计图

- 主路径垂直；分支左右分明；参照常见 BPMN/流程图读法。
