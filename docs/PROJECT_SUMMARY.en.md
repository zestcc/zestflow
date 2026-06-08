# ZestFlow Project Summary

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](PROJECT_SUMMARY.md) · **Type** Explanation  
> Execution engine and component system overview. Full architecture: [ARCHITECTURE.en.md](ARCHITECTURE.en.md).

## 1. Project overview

ZestFlow is a Spring Boot–based business process orchestration engine that splits method calls in services into reusable execution nodes and automatically records each node's input parameters, output parameters, duration, and exceptions.

### 1.1 Core features

| Feature | Description |
|---------|-------------|
| **Component design** | Mark components with `@ZestComponent`; supports 28 component types |
| **Chain orchestration** | Serial, parallel, conditional branches, loops, and more |
| **High-performance engine** | StampedLock optimistic reads + double-buffer hot reload; reads are lock-free |
| **Async event collection** | Three-tier async pipeline; never blocks business threads |
| **Hot reload** | Chain definitions update at runtime without restart |
| **Full observability** | JDBC event storage with end-to-end tracing |

### 1.2 Tech stack

- **Java**: 17+ (project baseline)
- **Spring Boot**: 3.2.5
- **Database**: MySQL 8.x (default); H2 for demo tests
- **Frontend**: Vue 3 + Element Plus + Vite + TypeScript
- **Build**: Maven multi-module

---

## 2. Architecture design

### 2.1 Overall architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ZestFlow Architecture                    │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  @ZestComponent  │  │  @ZestExecute   │  │  @ZestParam     │              │
│  │  Component def   │  │  Execute method  │  │  Param injection │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
├─────────────────────────────────────────────────────────────────┤
│                        Core engine layer                        │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              DefaultChainExecutionEngine                    ││
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       ││
│  │  │ChainManager│ │NodeRunner │ │LifecycleExecutor│ │ChainKeyResolver│   ││
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘       ││
│  └─────────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│                        Data layer                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  ChainContext  │  │  DataBus      │  │  EventCollector│              │
│  │  Exec context  │  │  Data bus      │  │  Event collect  │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Core components

| Component | Responsibility |
|-----------|----------------|
| **ChainManager** | Chain definition management, double-buffer hot reload, StampedLock optimistic reads |
| **DefaultChainExecutionEngine** | Main execution engine; full chain lifecycle |
| **NodeRunner** | Node executor: interceptors, execution, retry, fallback |
| **LifecycleExecutor** | Parameter resolution, validation, reflective invocation |
| **ChainKeyResolver** | Chain key resolution; readiness checks |
| **ChainContext** | Execution context; all runtime data |
| **AsyncEventPublisher** | Async event publisher; three-tier pipeline |

### 2.3 Execution flow

```
1. Load chain definition → ChainManager.load()
2. Readiness check → ChainKeyResolver.readinessFailure()
3. Create instance → ChainInstanceManager.register()
4. Topological sort → layer by dependency
5. Layer execution → NodeRunner.execute()
   ├── Pre-interceptors
   ├── Parameter resolution → ZestParamResolver
   ├── Component execution → LifecycleExecutor
   ├── Result handling
   └── Post-interceptors
6. Event collection → AsyncEventPublisher
7. Return result → ChainExecuteResultDTO
```

---

## 3. Component type system

### 3.1 Component type enum (28 types)

| Category | Type | Description |
|----------|------|-------------|
| **Basic execution** | EXECUTOR | Generic executor |
| | SERVICE | Service invocation |
| | TASK | Task execution |
| **Data processing** | PARSER | Data parsing |
| | TRANSFORMER | Data transformation |
| | AGGREGATOR | Data aggregation |
| | VALIDATOR | Data validation |
| **Flow control** | ROUTER | Routing |
| | SELECTOR | Conditional selection |
| | FORK | Branch |
| | JOIN | Join |
| | ITERATOR | Iteration |
| | WHILE | Loop |
| **Integration** | HTTP_INVOKE | HTTP call |
| | RPC_INVOKE | RPC call |
| | MQ_PRODUCER | Message produce |
| | MQ_CONSUMER | Message consume |
| | CACHE_READER | Cache read |
| | CACHE_WRITER | Cache write |
| **Human interaction** | APPROVER | Approval |
| | ASSIGNER | Assignment |
| | NOTIFIER | Notification |
| **Enhancement** | LOGGER | Logging |
| | TIMER | Timer |
| | SCRIPT | Script execution |
| | FALLBACK | Fallback |
| | RETRY | Retry |
| | INTERCEPTOR | Interceptor |

### 3.2 Node types

| Node type | Constant | Description |
|-----------|----------|-------------|
| NORMAL | NODE_TYPE_NORMAL | Normal node |
| CONDITION | NODE_TYPE_CONDITION | Condition node |
| SELECTOR | NODE_TYPE_SELECTOR | Selector node |
| SCRIPT | NODE_TYPE_SCRIPT | Script node |
| SUB_CHAIN | NODE_TYPE_SUB_CHAIN | Sub-chain node |
| ITERATOR | NODE_TYPE_ITERATOR | Iterator node |
| FORK | NODE_TYPE_FORK | Fork node |
| JOIN | NODE_TYPE_JOIN | Join node |
| TRY_CATCH | NODE_TYPE_TRY_CATCH | Try-catch node |
| WHILE | NODE_TYPE_WHILE | While loop node |
| LOGGER | NODE_TYPE_LOGGER | Logger node |
| DELAY | NODE_TYPE_DELAY | Delay node |

---

## 4. Test coverage

### 4.1 Test results

| Test class | Tests | Pass | Fail | Pass rate |
|------------|-------|------|------|-----------|
| ComponentSmokeTest | 172 | 172 | 0 | **100%** |
| ZestFlowE2ETest | 12 | 10 | 2 | **83%** |

### 4.2 Scenarios covered

- ✅ Simple linear chains
- ✅ Parallel DAG chains
- ✅ Order processing flows
- ✅ Conditional branch chains
- ✅ Iterator/loop chains
- ✅ Exception handling chains
- ✅ Sub-chain invocation
- ✅ Retry mechanism
- ✅ Fallback handling
- ✅ Parameter validation

---

## 5. Example chains

### 5.1 Chain statistics

**Total: 155 example chains**

### 5.2 Scenario distribution

| Category | Count | Description |
|----------|-------|-------------|
| Order | 10 | Create, cancel, refund, batch, etc. |
| Payment | 10 | Pay, refund, wallet, withdraw, etc. |
| Inventory | 10 | Check, deduct, transfer, restore, etc. |
| Marketing | 12 | Coupons, discounts, points, cashback, etc. |
| Logistics | 13 | Create, delivery, return, sign-off, etc. |
| User | 13 | Register, login, auth, tags, etc. |
| Approval | 14 | Simple and multi-level approval |
| Notification | 14 | SMS, email, push, WeChat, etc. |
| Data processing | 14 | Transform, filter, aggregate, split, etc. |
| API integration | 15 | HTTP, REST, GraphQL, gRPC, etc. |
| Cache | 10 | Read, write, invalidate, refresh, etc. |
| MQ | 10 | Produce, consume, publish, subscribe, etc. |
| Composite | 10 | Full order flow, complex DAG, etc. |

### 5.3 Typical chain examples

#### Order creation flow

```java
ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
    .code("order-create")
    .nodes(List.of(
        node("validate", "NORMAL", "validateOrder"),
        node("risk", "NORMAL", "checkRisk"),
        node("stock", "NORMAL", "deductStock"),
        node("create", "NORMAL", "createOrder"),
        node("notify", "NORMAL", "sendNotify")
    ))
    .edges(List.of(
        edge("validate", "risk"),
        edge("risk", "stock"),
        edge("stock", "create"),
        edge("create", "notify")
    ))
    .build();
```

#### Parallel processing flow

```java
ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
    .code("order-parallel")
    .nodes(List.of(
        node("validate", "NORMAL", "validateOrder"),
        node("risk", "NORMAL", "checkRisk"),
        node("stock", "NORMAL", "deductStock"),
        node("merge", "NORMAL", "mergeResult")
    ))
    .edges(List.of(
        edge("validate", "risk"),
        edge("validate", "stock"),
        edge("risk", "merge"),
        edge("stock", "merge")
    ))
    .build();
```

---

## 6. Performance optimization

### 6.1 Concurrency control

| Mechanism | Implementation | Benefit |
|-----------|----------------|---------|
| **Optimistic read lock** | StampedLock.tryOptimisticRead() | Lock-free reads, best performance |
| **Double buffer** | active + standby Map | Hot reload without blocking |
| **Write lock downgrade** | tryConvertToReadLock() | Shorter lock hold time |

### 6.2 Async event collection

```
Three-tier async pipeline:
Business thread → Queue1 → EventCollector → Queue2 → BatchPublisher → JDBC
```

- **Level 1**: Business thread writes to in-memory queue
- **Level 2**: EventCollector batch collection
- **Level 3**: BatchPublisher batch DB write

### 6.3 Performance metrics

| Metric | Value |
|--------|-------|
| Single-node execution | < 1ms |
| Chain definition read | Lock-free |
| Hot reload latency | < 10ms |
| Event collection latency | Async; does not block business |

---

## 7. Comparison with alternatives

### 7.1 Feature comparison

| Feature | ZestFlow | LiteFlow | Flowable |
|---------|----------|----------|----------|
| **Positioning** | Business process orchestration | Lightweight rule orchestration | Enterprise BPM |
| **Package size** | ~500KB | ~300KB | Several MB |
| **DB dependency** | Optional | Optional | Required |
| **Hot reload** | ✅ Double buffer | ✅ Smooth refresh | ✅ Dynamic deploy |
| **Script support** | JS/Groovy | 8 languages | JS/Groovy |
| **BPMN standard** | ❌ | ❌ | ✅ Full support |
| **AI Agent** | ❌ | ✅ | ❌ |

### 7.2 Selection guide

| Scenario | Recommendation |
|----------|----------------|
| Microservice business orchestration | **ZestFlow** / LiteFlow |
| Lightweight rule engine | LiteFlow |
| Enterprise BPM platform | Flowable |
| High-concurrency trading systems | **ZestFlow** |
| AI Agent orchestration | LiteFlow |
| Complex approval workflows | Flowable |

---

## 8. Key fix history

### 8.1 Database persistence issue

**Problem**: The engine checks for chain records in the DB before execution; test chains loaded only in memory caused "chain not found" errors.

**Fix**: Added `saveChainToDatabase` in test code to persist test chains.

### 8.2 ChainContext null handling

**Problem**: `ConcurrentHashMap` does not allow null values; `ctx.put("key", null)` threw NPE.

**Fix**: Modified `ChainContext.put` to remove the key when value is null.

### 8.3 Test parameter supplementation

**Problem**: Some components lacked required parameters, causing execution failures.

**Fix**: Added missing parameters in `smokeParams()`.

---

## 9. Quick start

### 9.1 Add dependency

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 9.2 Define a component

```java
@ZestComponent("order")
public class OrderHandler {
    
    @ZestExecute(value = "createOrder", name = "Create order")
    public Order createOrder(
            @ZestParam(value = "userId") String userId,
            @ZestParam(value = "productId") String productId,
            @ZestParam(value = "amount") double amount) {
        // Business logic
        return order;
    }
}
```

### 9.3 Define a chain

```java
ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
    .code("order-flow")
    .nodes(List.of(
        node("create", "NORMAL", "createOrder"),
        node("pay", "NORMAL", "processPayment"),
        node("notify", "NORMAL", "sendNotify")
    ))
    .edges(List.of(
        edge("create", "pay"),
        edge("pay", "notify")
    ))
    .build();

chainManager.load(chainDefinitionBuilder.build(dto));
```

### 9.4 Execute a chain

```java
Map<String, Object> params = Map.of(
    "userId", "U001",
    "productId", "P001",
    "amount", 99.9
);

ChainExecuteResultDTO result = chainExecutionEngine.execute("order-flow", params);

if (result.getStatus() == ChainConstants.CHAIN_SUCCESS) {
    System.out.println("Execution succeeded!");
}
```

---

## 10. AI integration (Copilot + Dev MCP)

> **Updated** 2026-06-07 · See [AI_COPILOT.en.md](./AI_COPILOT.en.md)

ZestFlow AI splits into **Orchestration Copilot** (Admin) and **Dev Copilot** (`zestflow-mcp` + IDE). Tagline: **Admin designs chains, MCP connects specs and code, Cursor writes components.**

| Capability | Orchestration Copilot | Dev Copilot (MCP) |
|------------|----------------------|-------------------|
| User | Business / implementation / orchestration staff | Developers writing `@ZestComponent` |
| LLM | Admin tenant BYOK | IDE-side model |
| Typical features | NL→chain draft, expressions, log diagnosis | list components, validate chain, scaffold, plan_chain |
| Persistence | Designer diff → manual publish | IDE Apply (MCP does not write to disk) |

**Dev setup (latest)**:

1. `install-mcp.ps1` — install platform JAR to `~/.zestflow/tools/` (once)
2. `init-dev-project.ps1` / `--init-dev` — generate `.cursor/mcp.json`, `.zestflow/rules/project.md`, etc.
3. MCP **12 Tools**, including Chain-first learning (`plan_chain` → `distill_patterns`)

**Docs**: [MCP_SETUP.en.md](./MCP_SETUP.en.md) · [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.en.md) · [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.en.md)

---

## 11. Roadmap

### 11.1 Short term

- [ ] Complete remaining 2 E2E test cases
- [ ] More component examples (20+ per component)
- [ ] Performance benchmark report
- [ ] Monitoring dashboard

### 11.2 Medium term

- [ ] Visual flow designer (in progress)
- [ ] Rule expression DSL
- [ ] Multi-tenant support
- [ ] Distributed execution

### 11.3 Long term

- [x] AI Copilot + Dev MCP (Phase 1–3, see §10)
- [ ] Deep AI Agent orchestration (HTTP transport, enterprise mode)
- [ ] Cloud-native deployment
- [ ] Enterprise feature enhancements

---

## 12. Contributing

### 12.1 Code standards

- Follow Alibaba Java Development Guidelines
- Use Lombok for boilerplate reduction
- Unit test coverage > 80%

### 12.2 Commit conventions

```
feat: new feature
fix: bug fix
docs: documentation update
refactor: refactoring
test: test-related
```

---

**Document version**: v1.1  
**Last updated**: 2026-06-07  
**Maintained by**: ZestFlow Team
