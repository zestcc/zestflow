# ZestFlow Copilot 链定义要点

## 唯一规则
见 `ai-generation-acceptance.md`：验收标准 + 检索 RAG + 对标业界 + 高置信自动蒸馏闭环。

## chainData 结构
- 顶层含 `nodes` 与 `edges` 数组
- 节点需有唯一 `id`；业务节点引用已注册 `componentId`
- 条件边使用 Aviator；读取上下文：`chainCtx.get(ctx, 'key')`

## 判断节点 CONDITION
- `predicateMode`: `script`（内联 Aviator）或 `bind`（绑定 PREDICATE 元件）
- 脚本模式需 `predicateScript`；出边 label 与 trueLabel/falseLabel 一致

## 校验闭环
- Copilot 提议必须经 Executor `validate-definition` 通过后再应用到画布
- 设计域交付门禁：`POST /ai/delivery/validate`（chainData lifecycle=production + validate_chain）
- 完整工程门禁：MCP `validate_delivery(projectRoot, strictMode=true)`
- 禁止编造未在 allowedComponents 中的 componentId

## 交付管道（不可跳过）
search_patterns → plan_chain → compose_chain（/ai/chains/compose）→ validate_chain
→ gen_smoke_suite → run_acceptance_suite → validate_delivery(passed=true)

## bootstrap vs production
- bootstrap：DeclarationSync 占位链，设计器黄色提示，不可发布
- production：compose_chain 或 ≥2 业务节点且校验通过；发布前须 validate_delivery

## 发布原则
- Copilot 不自动 save/publish/reload；人工确认后保存设计并发布链
