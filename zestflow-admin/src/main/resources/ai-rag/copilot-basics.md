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
- 禁止编造未在 allowedComponents 中的 componentId

## 发布原则
- Copilot 不自动 save/publish/reload；人工确认后保存设计并发布链
