# Aviator 表达式常用写法

## 上下文与空值
- 读取链上下文：`chainCtx.get(ctx, 'amount')`
- 字符串非空：`StringUtils.hasText(chainCtx.get(ctx, 'sku'))`
- 数值比较：`chainCtx.get(ctx, 'qty') > 0`

## 分支标签
- CONDITION 节点的 trueLabel/falseLabel 必须与出边 label 匹配
- 选择器 SELECTOR 出边 label 对应 tag 名称

## 常见错误
- 不要用 Java 语法；Aviator 不支持 `obj.field`，用 map/ctx 读取
- 避免编造上下文字段名；不确定时在 summary 中列出需人工确认项

## 安全与限制（Executor 内置）
- 禁止 `Runtime`、`System`、`java.lang.*`、反射等（静态黑名单）
- 单次执行超时默认 5000ms（`zestflow.executor.expression.timeout-ms`）
- 循环上限默认 10000 次（`max-loop-count`）
- 条件求值失败默认视为 false（fail-closed）；SCRIPT 节点失败则报错
- 超过约 5 行或需 IO/DB 的逻辑应写成 `@ZestExecute` / `@ZestPredicate` 元件
