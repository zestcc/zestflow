# Demo 全链路矩阵（demo-app）

## 元件（Java，非仅 SQL）

| 模块 | 类 | 说明 |
|------|-----|------|
| 订单/售后 | `OrderHandler` | `createOrder`、`findAfterSale`、`auditAfterSale`(PREDICATE)、`handleAfterSale`(SELECTOR)、退货/换货/退款/发货 |
| 用户 | `UserHandler` | `validateUser`、`sendNotify` |
| 库存 | `InventoryHandler` | 10 个执行节点 |
| 支付 | `PaymentHandler` | 10 个执行节点 |
| 风控 | `RiskHandler` | 5 个 PREDICATE + 5 个执行节点 |
| 营销 | `MarketingHandler` | **新增** `routePromotion`(SELECTOR) |
| 演示 | `DemoContextHandler` | **新增** `seedNotifyItems`、`noopStep`（压力链填充） |
| 审计/财务/物流/消息 | 各 `*Handler` | 见源码 `@ZestExecute` |

上次会话**未新增**业务 Handler，仅复用已有注解元件；本次补充 `DemoContextHandler`、`routePromotion`、`riskCheckAmount` 分支标签。

## 节点规模分档（不含开始/结束）

| 档位 | 链编码 | 业务节点数 | 图结构 |
|------|--------|-----------|--------|
| 1–3 | `CHN_DEMO_NODE_1` | 1 | 单任务 |
| 1–3 | `CHN_DEMO_NODE_2` | 2 | 串行 |
| 1–3 | `CHN_DEMO_ORDER_CREATE` 等 20+ 条 | 3 | 串行 |
| 1–3 | `CHN_DEMO_SCRIPT_GATE` | 3 | NORMAL→**SCRIPT**→NORMAL |
| 1–3 | `CHN_DEMO_SUB_ORDER` | 3 | NORMAL→**SUB_CHAIN**→NORMAL |
| 5–10 | `CHN_DEMO_PAYMENT_RISK` | 8 | 条件分支（金额风控） |
| 5–10 | `CHN_DEMO_ORDER_PIPELINE` | 10 | 串行+用户风控条件 |
| 5–10 | `CHN_DEMO_MARKETING_BRANCH` | 7 | 设备风控+**SELECTOR** 促销路由 |
| 5–10 | `CHN_DEMO_ITER_NOTIFY` | 3 | **ITERATOR** 批量短信 |
| 5–10 | `CHN_DEMO_AFTER_SALE` | 7 | 完整 DAG（条件+多分支，见 initData 大 JSON） |
| 70–80 | `CHN_DEMO_STRESS_75` | **75** | 75 步串行（真实元件+noop 填充） |

## 节点类型覆盖

| 引擎类型 | 示例元件/节点 |
|----------|----------------|
| NORMAL | 各 Handler `@ZestExecute` |
| CONDITION + PREDICATE | `riskCheckAmount`、`auditAfterSale`、`riskCheckDevice` |
| CONDITION + SELECTOR | `handleAfterSale`、`routePromotion` |
| SCRIPT | `CHN_DEMO_SCRIPT_GATE` n2 Groovy |
| SUB_CHAIN | `CHN_DEMO_SUB_ORDER` → `CHN_DEMO_ORDER_CREATE` |
| ITERATOR | `CHN_DEMO_ITER_NOTIFY` + `notifyItems` |

## 试验场场景

共 **34** 条 `playground_scene`（含售后 API + **失败继续** `SCN20260602000001`），映射上表各档链路。

## E2E 策略（与黑盒脚本对齐）

| 档位 | 策略 Profile | 说明 |
|------|----------------|------|
| 全绿 | `fullGreen` | 全部 demo-app 场景 `status=1`，含 75 步压力链 |
| 一部分绿 | `partialGreen` + 链 `CHN_DEMO_CONTINUE_ON_ERROR` | 链级 `errorStrategy=CONTINUE`，中段 `failStep` 失败仍整链成功 |
| 报错跳过 | `skipOnError` | `optionalScenes` 失败记 skipped，不导致 exit 1 |

配置：`scripts/blackbox/e2e-scene-policy.json`，执行：`run-full-e2e.ps1 -E2eProfile <name>`。

## 生成与灌库

```powershell
powershell -File scripts/init.ps1
powershell -File scripts/initData.ps1
```

`CHN_DEMO_AFTER_SALE` 复杂 graph 仍由 `initData.sql` 内手工块保留（与编辑器导出一致）。
