# Aviator 表达式约定

## 链内条件边

- 引擎：Aviator 5.x（与 Executor 一致）  
- 上下文：`chainCtx.get(ctx, 'key')` 或项目约定写法  
- 比较：`==`、`!=`、`>`、`<`、`&&`、`||`

## 示例

```javascript
chainCtx.get(ctx, 'amount') > 100
chainCtx.get(ctx, 'status') == 'PAID'
```

## 注意

- 键名与 Playground / 设计器 ctx 字段一致  
- 复杂逻辑优先拆到 `@ZestPredicate` 元件  
- 表达式助手在 **Admin Orchestration Copilot**；元件开发在 **IDE + MCP**
- 禁止 `Runtime`/`System`/`java.lang.*`；默认超时 5000ms、循环上限 10000（`zestflow.executor.expression.*`）
- 条件失败默认 false（fail-closed）；SCRIPT 失败抛错
