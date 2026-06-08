# Delivery DoD — 生产级交付完成定义

## usable_score 公式

```
usable_score = 0.35 * chain_topology_score
             + 0.25 * playground_coverage
             + 0.30 * smoke_pass_rate
             + 0.10 * doc_java_doc_score
```

生产级：`usable_score ≥ 0.95` 且 `blocking_issues = 0`。

## MCP 工具

- `validate_delivery` — 汇总门禁报告
- `compose_chain` — Pattern 实例化 production 链
- `gen_smoke_suite` / `run_acceptance_suite` — 全流程冒烟

## Agent 禁止项

- 禁止仅改 Handler 无链/设计图
- 禁止单节点 Seeder 冒充 production
- 禁止跳过 validate_delivery 宣称完成
