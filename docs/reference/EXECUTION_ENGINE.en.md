# Execution Engine Reference

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](EXECUTION_ENGINE.md)  
> **Core interface:** `com.zestflow.executor.engine.ChainExecutionEngine`

---

## 1. Overview

The execution engine is responsible for:

1. Loading chain definitions (`ChainLoader` + `ChainRepository`)
2. DAG topological sort and layered parallel execution
3. Invoking `NodeRunner` per node (interceptors → parameter resolution → reflective execution → event publishing)
4. Aggregating results into `ChainExecuteResultDTO`

> **Note:** Documentation often refers to "chain management" via `ChainLoader`, `ChainRepository`, and `ChainInstanceManager`. There is **no `ChainManager` interface**.

---

## 2. ChainExecutionEngine API

### 2.1 execute (Simple Mode)

```java
ChainExecuteResultDTO execute(String chainCode, Object... args);
```

| Parameter | Description |
|-----------|-------------|
| `chainCode` | Published chain code |
| `args` | Business objects; the engine registers them in DataBus by **type**, and component methods declare matching parameter types for injection |

```java
OrderDTO order = new OrderDTO("U001", 99.9);
ChainExecuteResultDTO result = engine.execute("order-flow", order);
OrderCreatedResult created = result.getData(OrderCreatedResult.class);
```

### 2.2 execute (Advanced Mode)

```java
ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args);
ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params,
                              Map<String, String> headers, Object... args);
```

| Parameter | Description |
|-----------|-------------|
| `params` | Explicit DataBus key-value pairs (may be null) |
| `headers` | HTTP header forwarding (Mode 1/2) |
| `args` | Typed business objects |

### 2.3 executeWithDeadline

```java
ChainExecuteResultDTO executeWithDeadline(String chainCode, Map<String, Object> params,
                                          long parentDeadlineMs);
```

Sub-chain nodes inherit the parent chain's absolute deadline; `Long.MAX_VALUE` means no parent constraint.

### 2.4 executeAsync

```java
CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Object... args);
```

Asynchronous execution; suitable for long-running chains.

### 2.5 Termination and Query

| Method | Return | Description |
|--------|--------|-------------|
| `stop(instanceId)` | boolean | Stop a single instance |
| `stopByChain(chainCode)` | int | Stop all running instances of a chain |
| `listRunning(chainCode)` | List\<ChainInstance\> | Query running instances |

---

## 3. ChainExecuteResultDTO

| Method | Description |
|--------|-------------|
| `isSuccess()` | `status == CHAIN_SUCCESS(4)` |
| `getData(Class<T>)` | Get result by type (prefers typedData → resultData) |
| `getReturnValue()` | Chain final return value (PARSER, etc.) |
| `getReturnValue(Class<T>)` | Typed final return value |

| Field | Description |
|-------|-------------|
| `instanceId` | Execution instance ID |
| `nodeResults` | Per-node details |
| `resultData` | DataBus snapshot |
| `finalReturnValue` | Last successful node returnValue |
| `failedNodeId` | Failed node |
| `errorCode` / `errorMessage` | Error information |

---

## 4. ChainContext (Execution Context)

```java
<T> T get(String key, Class<T> type);
void put(String key, Object value);
<T> T getOrDefault(String key, Class<T> type, T defaultValue);
void putAll(Map<String, Object> map);
boolean containsKey(String key);
void remove(String key);
```

DataBus passes data between nodes; `@ZestParam` injects from DataBus by key.

---

## 5. Chain Loading and Hot Reload

| Class | Responsibility |
|-------|----------------|
| `ChainLoader` | Startup load, validation, hot reload |
| `ChainRepository` | Chain CRUD, version snapshots |
| `ChainInstanceManager` | Running instance registry |

**Hot reload:** When `zestflow.executor.chain.auto-reload=true` (default), Admin publish or `PUT /api/chains/{code}/reload` triggers `ChainLoader` double-buffer reload — **no restart required**.

---

## 6. Error Strategies

Configurable at chain / node level:

| Strategy | Constant | Behavior |
|----------|----------|----------|
| Stop | `STOP` | Terminate on failure |
| Continue | `CONTINUE` | Skip failed node |
| Retry | `RETRY` | Retry per `retryCount` / `retryIntervalMs` |

---

## 7. HTTP Dual Channel

| Channel | Entry | Response |
|---------|-------|----------|
| **Netty** | `POST :20550/execute` | Fixed full `ChainExecuteResultDTO` |
| **Tomcat** | `POST :8081/execute` (requires `execute-endpoint-enabled=true`) | Controlled by `execute-response-mode`: BODY / DETAIL |

Admin Playground and schedule callbacks **must** use the Netty channel.

---

## 8. Event Publishing

Each node lifecycle emits `ChainEvent` (`CHAIN_STARTED`, `NODE_COMPLETED`, `NODE_FAILED`, etc.), written to Collector asynchronously in batches via `AsyncEventPublisher`.

**Principle:** Collection must never block business threads (bounded queue + circuit breaker + optional disk fallback).

---

## Related Documentation

- [API.en.md](API.en.md) — `/execute` request format
- [ANNOTATIONS.en.md](ANNOTATIONS.en.md) — Component annotations
- [SPI.en.md](SPI.en.md) — EventCollector extension
- [FAQ.en.md](FAQ.en.md) — Common execution questions
- [ARCHITECTURE.md](../ARCHITECTURE.en.md) §5.2 — Engine internals
