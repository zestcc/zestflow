# Chain Orchestration Guide

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** How-to Guide · **Language** English · [简体中文](CHAIN_ORCHESTRATION.md)

This guide explains how to create, publish, schedule, and validate execution chains using the visual designer in Admin.

---

## 1. Concept relationships

```text
Design  ──bind──▶  Chain  ──publish──▶  Executor hot reload
   │                    │
   └─ X6 graph JSON     └─ Cron schedule / manual / API trigger
```

| Entity | Description |
|--------|-------------|
| **Design** | DAG graph definition (nodes, edges, layout); code prefix `DSN` |
| **Chain** | Executable instance linked to a design; code prefix `CHN` |
| **Node** | Smallest execution unit in a chain; binds to a component or control logic |

Terminology: [reference/GLOSSARY.en.md](../reference/GLOSSARY.en.md).

---

## 2. Create a design

1. **Design management** → **New design**, enter a name (code auto-generated as `DSNyyyyMMddxxxxxx`)
2. Open the **design editor** (AntV X6)
3. Drag nodes from the left panel onto the canvas

### Node types

| Type | Purpose |
|------|---------|
| Start / End | Flow boundaries (structural nodes) |
| Executor | Invokes `@ZestExecute` components |
| Condition | Branching; references `@ZestPredicate` |
| Selector | Multi-way selection |
| Script | Aviator expression (see §10) |
| Sub-chain | Nests another chain |

Node type constants: `ChainConstants.NODE_TYPE_*`.

---

## 3. Configure node properties

Select a node and configure the right panel:

| Field | Description |
|-------|-------------|
| Name | Display label |
| Component ID / Component name | Bind to scanned components |
| Execution strategy | Serial / parallel, etc. |
| Parameter binder | Upstream output → current input |
| Pre / post processors | Interceptor chain |
| Error strategy | STOP / CONTINUE / RETRY |

Double-click an edge to edit its label; use the properties panel to switch line style (straight / orthogonal / curved).

---

## 4. Save and bind a chain

1. Toolbar **Save** → persists `graphData`
2. **Chain management** → **New chain**, select the linked design
3. In chain details, confirm all nodes and component bindings are complete

---

## 5. Publish to Executor

**Chain management** → select chain → **Publish**:

- Admin syncs chain definition to online Executors via `ExecutorProxyService`
- Executor `ChainManager` hot-reloads — **no business app restart required**
- Broadcasts reload when multiple Executor instances are online

Common publish failures: target Executor offline, component not scanned, DAG has cycles or is disconnected.

---

## 6. Execute and verify

### Playground (recommended)

**Playground → Scenes** — select the scene for your chain, enter JSON parameters, click Execute. Admin uses the **Netty `/execute`** channel and returns the full `ChainExecuteResultDTO` (including `instanceId`, `nodeResults`).

### Log tracing

**Log query** → filter by chain code / time → click trace to view:

- Per-node latency and status coloring
- Input / output JSON
- Execution graph PNG export

---

## 7. Scheduling

**Schedule center** → create a schedule task:

| Field | Description |
|-------|-------------|
| Chain code | Chain to trigger |
| Cron expression | Schedule rule |
| Route strategy | Round-robin / random / hash, etc. |
| Failure policy | Retry, alert (per configuration) |

**Architecture note:** Business Cron runs **autonomously on Executor** reading the business database. Already-loaded schedules still fire when Admin is offline (see [adr/SCHEDULING.md](../adr/SCHEDULING.en.md)).

---

## 8. Error strategies

Configurable at chain or node level:

| Strategy | Behavior |
|----------|----------|
| `STOP` | Terminate chain on failure |
| `CONTINUE` | Skip failed node and continue |
| `RETRY` | Retry per configured count and interval |

---

## 9. AI Copilot assistance

Admin includes a built-in chain orchestration Copilot supporting natural language → chain draft, expression generation, and diagnostics. See [AI_COPILOT.md](../AI_COPILOT.en.md).

---

## 10. Aviator expressions

ZestFlow uses [Aviator 5.x](https://github.com/killme2008/aviator) for **edge conditions, SCRIPT nodes, and While loop conditions** (not LiteFlow EL, not Groovy).

### 10.1 Expression vs component

| Scenario | Prefer | Why |
|----------|--------|-----|
| Simple compare / empty check / numeric range | **Aviator edge condition** | Lightweight, hot-reloadable, AI-friendly |
| Multi-field logic + external calls | **`@ZestPredicate` component** | Testable, observable, no sandbox limits |
| Data mapping / simple math | **SCRIPT node (Aviator)** | Transform context without new Java |
| > ~5 lines or IO/DB | **`@ZestExecute` component** | Scripts must not carry business logic |
| Chain topology | **Designer DAG** | Do not encode flow in expressions |

### 10.2 Syntax essentials

- Read context: `chainCtx.get(ctx, 'orderId')` or designer-normalized `ctx.get('orderId')`
- Write context: `chainCtx.put(ctx, 'key', value)` / `ctx.put('key', value)`
- Empty string: `StringUtils.hasText(status)`
- Prefix: `aviator:` optional; `groovy:` deprecated
- **Forbidden**: Java reflection, `Runtime`, `System`, `java.lang.*` (static blocklist)

Failed conditions default to **fail-closed** (`false`). SCRIPT failures fail the node.

### 10.3 Configuration (`zestflow.executor.expression.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `timeout-ms` | `5000` | Per-evaluation/script timeout |
| `max-script-length` | `10000` | Max expression length |
| `max-cache-size` | `1000` | Compile cache LRU size |
| `max-loop-count` | `10000` | Loop iteration cap |
| `condition-fail-open` | `false` | Fail-open for conditions |
| `clear-cache-on-chain-reload` | `true` | Clear cache on hot reload |

Defaults: `zestflow-executor/src/main/resources/application.yml`.

### 10.4 Examples

**Edge condition:**

```text
price > 100 && StringUtils.hasText(supplierType)
```

**SCRIPT node:**

```text
let total = long(price) * long(qty);
ctx.put('amount', total);
seq.map('amount', total)
```

See Admin RAG `aviator-expressions.md` and MCP rule `zestflow://rules/aviator`.

---

## Related documentation

- [COMPONENT_DEVELOPMENT.en.md](COMPONENT_DEVELOPMENT.en.md) — Write orchestrable components
- [ARCHITECTURE.md](../ARCHITECTURE.en.md) §6 — Core business flow sequence
- [DEPLOY.en.md](../DEPLOY.en.md) — Production publish considerations
