# 链编排指南

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** How-to Guide · [English](CHAIN_ORCHESTRATION.en.md)

本指南说明如何在 Admin 中使用可视化设计器创建、发布、调度与验证执行链。

---

## 1. 概念关系

```text
设计 (Design)  ──绑定──▶  链 (Chain)  ──发布──▶  Executor 热加载
     │                         │
     └─ X6 图 JSON              └─ 调度 Cron / 手动 / API 触发
```

| 实体 | 说明 |
|------|------|
| **设计** | DAG 图定义（节点、连线、布局），编码前缀 `DSN` |
| **链** | 可执行实例，关联设计，编码前缀 `CHN` |
| **节点** | 链中最小执行单元，绑定元件或控制逻辑 |

术语详见 [reference/GLOSSARY.md](../reference/GLOSSARY.md)。

---

## 2. 创建设计

1. **设计管理** → **新建设计**，填写名称（编码自动生成 `DSNyyyyMMddxxxxxx`）
2. 进入 **设计编辑器**（AntV X6）
3. 从左侧面板拖拽节点到画布

### 节点类型

| 类型 | 用途 |
|------|------|
| 开始 / 结束 | 流程起止（结构节点） |
| 执行器 | 调用 `@ZestExecute` 元件 |
| 条件 | 分支，引用 `@ZestPredicate` |
| 选择器 | 多路选择 |
| 脚本 | Aviator 表达式（见 §10） |
| 子链 | 嵌套另一条链 |

节点类型常量见 `ChainConstants.NODE_TYPE_*`。

---

## 3. 配置节点属性

选中节点，右侧面板配置：

| 字段 | 说明 |
|------|------|
| 名称 | 展示用 |
| 元件 ID / 元件名称 | 绑定扫描到的元件 |
| 执行策略 | 串行 / 并行等 |
| 参数绑定器 | 上游输出 → 当前入参 |
| 前置 / 后置处理器 | 拦截器链 |
| 错误策略 | STOP / CONTINUE / RETRY |

连线可双击编辑标签；属性面板可切换线型（直线 / 折线 / 曲线）。

---

## 4. 保存与绑定链

1. 工具栏 **保存** → 持久化 `graphData`
2. **链管理** → **新建链**，选择关联设计
3. 链详情中确认节点与元件绑定完整

---

## 5. 发布到 Executor

**链管理** → 选择链 → **发布**：

- Admin 通过 `ExecutorProxyService` 将链定义同步到在线 Executor
- Executor `ChainManager` 热加载，**无需重启**业务应用
- 多实例 Executor 时广播 reload

发布失败常见原因：目标 Executor 离线、元件未扫描到、DAG 存在环或未连通。

---

## 6. 执行与验证

### 试验场（推荐）

**试验场 → 场景** 选择链对应场景，填入 JSON 参数，点击执行。Admin 走 **Netty `/execute`** 通道，返回完整 `ChainExecuteResultDTO`（含 `instanceId`、`nodeResults`）。

### 日志追踪

**日志查询** → 按链编码 / 时间筛选 → 点击 trace 查看：

- 节点级耗时与状态着色
- 入参 / 出参 JSON
- 执行图 PNG 导出

---

## 7. 调度

**调度中心** → 新建调度任务：

| 字段 | 说明 |
|------|------|
| 链编码 | 要触发的链 |
| Cron 表达式 | 定时规则 |
| 路由策略 | 轮询 / 随机 / 哈希等 |
| 失败策略 | 重试、告警（视配置） |

**架构要点：** 业务 Cron 由 Executor 读业务库**自治执行**，Admin 离线时已加载的调度仍可触发（详见 [adr/SCHEDULING.md](../adr/SCHEDULING.md)）。

---

## 8. 错误策略

链级 / 节点级可配置：

| 策略 | 行为 |
|------|------|
| `STOP` | 失败即终止链 |
| `CONTINUE` | 跳过失败节点继续 |
| `RETRY` | 按配置重试次数与间隔 |

---

## 9. AI Copilot 辅助

Admin 内置链编排 Copilot，支持自然语言 → 链草稿、表达式生成与诊断。见 [AI_COPILOT.md](../AI_COPILOT.md)。

---

## 10. Aviator 表达式

ZestFlow 使用 [Aviator 5.x](https://github.com/killme2008/aviator) 作为**边条件、SCRIPT 节点、While 循环条件**的统一表达式引擎（非 LiteFlow EL，非 Groovy）。

### 10.1 何时用表达式 vs 元件

| 场景 | 推荐 | 原因 |
|------|------|------|
| 简单比较、字符串判空、数值范围 | **Aviator 边条件** | 轻量、可热更新、AI 可生成 |
| 多字段组合 + 调外部服务 | **`@ZestPredicate` 元件** | 可单测、可观测、无沙箱限制 |
| 数据映射 / 简单计算 | **SCRIPT 节点（Aviator）** | 不写 Java 也能变换上下文 |
| 超过 ~5 行或含 IO/DB | **`@ZestExecute` 元件** | 脚本不适合承载业务逻辑 |
| 链结构（串并联、分支） | **设计器 DAG** | 不要用表达式描述拓扑 |

### 10.2 语法要点

- 读取上下文：`chainCtx.get(ctx, 'orderId')` 或设计器自动转换的 `ctx.get('orderId')`
- 写入上下文：`chainCtx.put(ctx, 'key', value)` / `ctx.put('key', value)`
- 字符串判空：`StringUtils.hasText(status)`
- 前缀：`aviator:` 可省略；`groovy:` 已废弃
- **禁止** Java 反射、`Runtime`、`System`、`java.lang.*` 等（编译前静态拦截）

条件表达式求值失败时默认 **fail-closed**（视为 `false`，走 false 分支）。SCRIPT 节点失败则节点报错。

### 10.3 配置（`zestflow.executor.expression.*`）

| 属性 | 默认 | 说明 |
|------|------|------|
| `timeout-ms` | `5000` | 单次求值/脚本超时（毫秒） |
| `max-script-length` | `10000` | 表达式最大字符数 |
| `max-cache-size` | `1000` | 编译缓存 LRU 上限 |
| `max-loop-count` | `10000` | Aviator 循环次数上限 |
| `condition-fail-open` | `false` | 条件失败是否视为 true |
| `clear-cache-on-chain-reload` | `true` | 链热加载后清编译缓存 |

完整默认值见 `zestflow-executor/src/main/resources/application.yml`。

### 10.4 示例

**边条件：**

```text
price > 100 && StringUtils.hasText(supplierType)
```

**SCRIPT 节点：**

```text
let total = long(price) * long(qty);
ctx.put('amount', total);
seq.map('amount', total)
```

更多片段见 Admin RAG `aviator-expressions.md` 与 MCP 规则 `zestflow://rules/aviator`。

---

## 相关文档

- [COMPONENT_DEVELOPMENT.md](COMPONENT_DEVELOPMENT.md) — 编写可被编排的元件
- [ARCHITECTURE.md](../ARCHITECTURE.md) §6 — 核心业务流程时序
- [DEPLOY.md](../DEPLOY.md) — 生产环境发布注意事项
