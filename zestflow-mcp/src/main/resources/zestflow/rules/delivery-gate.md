# 交付门禁（Delivery Gate · 平台硬约束）

> 与 `ai-generation-acceptance.md` 配套：**验收标准**定义「做什么」，本规则定义「何时算完成」。

## 一条规则

**未完成 `validate_delivery(passed=true)` 禁止向用户声明功能已完成。**

## Delivery DoD（默认 strictMode=true）

| 维度 | 要求 |
|------|------|
| 链 lifecycle | 功能交付须 **production**；bootstrap 占位链仅 dev 启动 |
| 链拓扑 | Start → … → End 连通；production ≥2 业务节点 |
| graph 同步 | `graph_data` 与 `chain_data` 节点 id 一致 |
| Pattern | `.zestflow/patterns/` 有项目蒸馏（strict 必填） |
| Acceptance | `.zestflow/acceptance/journeys.yml` + `last-run.json` 全绿 |
| 反模式 | 单体 `@ZestExecute` >80 行须 compose 拆节点 |
| 评分 | `usable_score ≥ 0.95` 且 `blocking = 0` |

## 标准 MCP 管道（不可跳过）

```text
search_patterns
  → plan_chain
  → scaffold_component（gap）
  → compose_chain（Pattern 模板，非单节点）
  → validate_chain
  → gen_smoke_suite
  → run_acceptance_suite
  → validate_delivery(strictMode=true)
  → gen_playground_scene
  → record_learning_event → distill_patterns
```

## bootstrap vs production

| | bootstrap | production |
|---|-----------|------------|
| 来源 | DeclarationSync / Dev Seeder | compose_chain + Admin 发布 |
| 节点 | 1 业务节点允许 | ≥ DoD |
| validate_delivery | warn | **必须 pass** |
| 禁止 | 用 Seeder 冒充功能交付 | — |

## compose_chain Pattern 模板

- `auth-owned-write` — 校验 → 加载 → 鉴权 → 写 → 副作用
- `guest-gated-read` — 元数据 → 门禁 → 内容
- `publish-workflow` — 校验 → 草稿 → 转换 → 持久化 → 索引
- `paginated-list` — 解析 → count → fetch → mapVo
- `admin-decision` — 加载审核 → 状态校验 → 执行 → 通知

## CLI

```bash
java -jar zestflow-mcp.jar --project . --app-code YOUR_APP --validate-delivery
java -jar zestflow-mcp.jar --project . --app-code YOUR_APP --validate-delivery --no-strict
```
