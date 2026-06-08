# 术语表

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference

本文档统一 ZestFlow 中英文术语，供所有文档与 UI 翻译参照。

---

## 核心概念

| 中文 | 英文 | 定义 |
|------|------|------|
| 链 | Chain | 完整业务流程，由节点按 DAG 串联；编码前缀 `CHN` |
| 节点 | Node | 链中最小执行单元 |
| 元件 | Component | 可复用执行逻辑，通过 `@ZestComponent` + `@ZestExecute` 定义 |
| 设计 | Design | 可视化 DAG 图定义；编码前缀 `DSN` |
| 上下文 | Context | 链执行时的键值数据载体（`ChainContext`） |
| 执行器 | Executor | 嵌入业务应用的执行引擎，含 Netty 回调服务 |
| 采集器 | Collector | 监听执行事件并持久化，实现 `EventCollector` SPI |
| 触发器 | Trigger | 链启动方式：手动 / Cron / API / 事件 |
| 调度 | Schedule | 按 Cron 定时触发链 |
| 试验场 | Playground | Admin 内置链试跑与场景管理 |

---

## 运行时实体

| 中文 | 英文 | 定义 |
|------|------|------|
| 模块 | Module | 业务应用在 Admin 中的注册分组（通常对应 `appCode`） |
| 注册 | Register | Executor/Collector 启动时向 Admin 上报 |
| 心跳 | Heartbeat | Executor 定期向 Admin 报告存活 |
| 回调 | Callback | Admin 经 Netty 调用 Executor `/execute` |
| 追踪 ID | traceId / instanceId | 单次链执行实例标识 |
| 事件 | Event | 链/节点生命周期数据单元（`ChainEvent`） |

---

## 策略与状态

| 中文 | 英文 | 取值 / 说明 |
|------|------|------------|
| 路由策略 | Route Strategy | 轮询、随机、哈希、Failover 等 |
| 错误策略 | Error Strategy | `STOP` / `CONTINUE` / `RETRY` |
| 在线 | ONLINE | Executor 状态 `1` |
| 离线 | OFFLINE | 主动下线 `0` |
| 异常 | ABNORMAL | 心跳超时 `2` |

---

## 节点类型（部分）

| 常量 | 中文 | 说明 |
|------|------|------|
| `NORMAL` | 普通节点 | 默认执行器节点 |
| `CONDITION` | 条件节点 | 分支判断 |
| `SELECTOR` | 选择器 | 多路选择 |
| `SCRIPT` | 脚本节点 | Aviator 表达式 |
| `SUB_CHAIN` | 子链 | 嵌套执行 |
| `FORK` / `JOIN` | 分支 / 汇合 | 并行控制 |
| `WHILE` | 循环 | 条件循环 |
| `DELAY` | 延迟 | 定时等待 |

完整列表见 `ChainConstants` 与 [QUICK_REFERENCE.md](../QUICK_REFERENCE.md)。

---

## 通信通道

| 名称 | 端口 | 说明 |
|------|------|------|
| Netty 执行通道 | 20550 | Admin 试验场 / 链回调，固定 DETAIL 响应 |
| Tomcat 执行通道 | 8081 等 | 可选 `execute-endpoint-enabled`，响应模式可配 |
| Collector 查询 | 20650 | Admin 日志查询只读 API |

详见 [ARCHITECTURE.md](../ARCHITECTURE.md) §8 与 [adr/SCHEDULING.md](../adr/SCHEDULING.md)。

---

## 竞品对照（文档用语）

| 术语 | LiteFlow | xxl-job | ZestFlow |
|------|----------|---------|----------|
| 最小单元 | 组件 Component | Job Handler | 元件方法 `@ZestExecute` |
| 编排 | EL 规则链 | 无 DAG | 可视化 DAG |
| 调度中心 | 无 | 调度中心 | Admin（链 Cron + Executor 自治） |
| 日志 | 弱 | 任务日志 | 节点级事件 + 执行图 |
