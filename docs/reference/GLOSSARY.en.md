# Glossary

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](GLOSSARY.md)

This glossary standardizes ZestFlow terminology for documentation and UI translation.

---

## Core concepts

| English | 中文 | Definition |
|---------|------|------------|
| Chain | 链 | End-to-end business process as a DAG; code prefix `CHN` |
| Node | 节点 | Smallest execution unit in a chain |
| Component | 元件 | Reusable logic via `@ZestComponent` + `@ZestExecute` |
| Design | 设计 | Visual DAG definition; code prefix `DSN` |
| Context | 上下文 | Key-value carrier during execution (`ChainContext`) |
| Executor | 执行器 | Embedded engine + Netty callback service |
| Collector | 采集器 | Persists events via `EventCollector` SPI |
| Trigger | 触发器 | How a chain starts: manual / Cron / API / event |
| Schedule | 调度 | Cron-based chain trigger |
| Playground | 试验场 | Admin built-in chain trial run and scenarios |

---

## Runtime entities

| English | 中文 | Definition |
|---------|------|------------|
| Module | 模块 | Admin registration group (usually `appCode`) |
| Register | 注册 | Executor/Collector reports to Admin on startup |
| Heartbeat | 心跳 | Periodic Executor liveness to Admin |
| Callback | 回调 | Admin invokes Executor `/execute` via Netty |
| traceId / instanceId | 追踪 ID | Single chain execution instance identifier |
| Event | 事件 | Lifecycle data unit (`ChainEvent`) |

---

## Policies and status

| English | 中文 | Values / notes |
|---------|------|----------------|
| Route Strategy | 路由策略 | Round-robin, random, hash, failover, etc. |
| Error Strategy | 错误策略 | `STOP` / `CONTINUE` / `RETRY` |
| ONLINE | 在线 | Executor status `1` |
| OFFLINE | 离线 | Voluntary shutdown `0` |
| ABNORMAL | 异常 | Heartbeat timeout `2` |

---

## Node types (selected)

| Constant | English | Description |
|----------|---------|-------------|
| `NORMAL` | Normal | Default executor node |
| `CONDITION` | Condition | Branch on predicate |
| `SELECTOR` | Selector | Multi-way selection |
| `SCRIPT` | Script | Aviator expression |
| `SUB_CHAIN` | Sub-chain | Nested chain |
| `FORK` / `JOIN` | Fork / Join | Parallel control |
| `WHILE` | Loop | Conditional loop |
| `DELAY` | Delay | Timed wait |

See `ChainConstants` and [QUICK_REFERENCE.en.md](../QUICK_REFERENCE.en.md).

---

## Communication channels

| Channel | Port | Description |
|---------|------|-------------|
| Netty execute | 20550 | Admin Playground / callbacks; fixed DETAIL response |
| Tomcat execute | 8081, etc. | Optional `execute-endpoint-enabled`; configurable response mode |
| Collector query | 20650 | Admin log read API |

See [ARCHITECTURE.en.md](../ARCHITECTURE.en.md) and [adr/SCHEDULING.md](../adr/SCHEDULING.en.md).

---

## Competitor terminology

| Term | LiteFlow | xxl-job | ZestFlow |
|------|----------|---------|----------|
| Smallest unit | Component | Job Handler | `@ZestExecute` method |
| Orchestration | EL rule chain | No DAG | Visual DAG |
| Control plane | None | Scheduler | Admin (+ Executor-autonomous Cron) |
| Observability | Weak | Task logs | Per-node events + execution graph |
