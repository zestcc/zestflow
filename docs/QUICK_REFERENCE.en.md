# Quick Reference

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](QUICK_REFERENCE.md)

High-frequency lookup tables; **full documentation** is in `docs/reference/` dedicated pages.

| Topic | Detailed Documentation |
|-------|------------------------|
| REST / Netty API | [API.en.md](reference/API.en.md) |
| Full annotation set | [ANNOTATIONS.en.md](reference/ANNOTATIONS.en.md) |
| Execution engine | [EXECUTION_ENGINE.en.md](reference/EXECUTION_ENGINE.en.md) |
| SPI extensions | [SPI.en.md](reference/SPI.en.md) |
| Configuration | [CONFIGURATION.en.md](reference/CONFIGURATION.en.md) |
| FAQ | [FAQ.en.md](reference/FAQ.en.md) |
| OpenAPI | [OPENAPI.en.md](reference/OPENAPI.en.md) |

---

## 1. Core Annotations (Summary)

| Annotation | Purpose |
|------------|---------|
| `@ZestComponent("code")` | Component group |
| `@ZestExecute(value, name)` | Orchestratable method |
| `@ZestParam` | Parameter injection |
| `@ZestPredicate` | Conditional branch |
| `@ZestSelector` + `@ZestTag` | Multi-way selection |
| `@ZestChain("key")` | Chain placeholder declaration |

---

## 2. Chain / Node Status Codes

**Chain runtime status (`ChainExecuteResultDTO.status`):**

| Value | Constant | Meaning |
|-------|----------|---------|
| 0 | `CHAIN_INIT` | Initialized |
| 1 | `CHAIN_LOADING` | Loading |
| 2 | `CHAIN_READY` | Ready |
| 3 | `CHAIN_RUNNING` | Running |
| 4 | `CHAIN_SUCCESS` | Success |
| 5 | `CHAIN_FAILED` | Failed |
| 6 | `CHAIN_TIMEOUT` | Timed out |
| 8 | `CHAIN_STOPPED` | Stopped |

**Node status (`NodeResultDTO.status`):**

| Value | Constant | Meaning |
|-------|----------|---------|
| 3 | `NODE_SUCCESS` | Success |
| 4 | `NODE_FAILED` | Failed |
| 8 | `NODE_SKIPPED` | Skipped |

---

## 3. Node Types (`ChainConstants.NODE_TYPE_*`)

| Constant | Description |
|----------|-------------|
| `NORMAL` | Standard executor |
| `CONDITION` | Condition |
| `SELECTOR` | Selector |
| `SCRIPT` | Aviator script |
| `SUB_CHAIN` | Sub-chain |
| `FORK` / `JOIN` | Parallel fork / join |
| `WHILE` | Loop |
| `ITERATOR` | Iterator |
| `TRY_CATCH` | Exception handling |
| `DELAY` / `LOGGER` | Delay / log |
| `HTTP_CLIENT` / `MQ_*` / `CACHE_*` | Integration types |

---

## 4. Error Strategies

| Constant | Behavior |
|----------|----------|
| `STOP` | Terminate on failure |
| `CONTINUE` | Skip and continue |
| `RETRY` | Automatic retry |

---

## 5. Core API Quick Reference

### ChainExecutionEngine

```java
ChainExecuteResultDTO execute(String chainCode, Object... args);
ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args);
CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Object... args);
boolean stop(String instanceId);
```

### ChainContext

```java
<T> T get(String key, Class<T> type);
void put(String key, Object value);
boolean containsKey(String key);
```

### HTTP Execution (Netty)

```bash
POST http://localhost:20550/execute
Content-Type: application/json

{"chainCode":"CHN...","params":{"userId":"U001"}}
```

### Chain Management (Executor Side)

| Class | Methods |
|-------|---------|
| `ChainLoader` | Startup load, hot reload |
| `ChainRepository` | CRUD, versioning |
| `ChainInstanceManager` | Running instances |

---

## 6. Ports

| Port | Component |
|------|-----------|
| 8080 | Admin |
| 8081 | Business Tomcat (Demo) |
| 20550 | Executor Netty |
| 20650 | Collector Netty |

---

## 7. Minimal Configuration

```yaml
spring.application.name: my-app
zestflow:
  executor:
    admin-addresses: http://localhost:8080
    port: 20550
  collector:
    registry:
      port: 20650
```

---

## 8. Maven Dependency

```xml
<dependency>
  <groupId>cn.zestflow.www</groupId>
  <artifactId>zestflow-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

---

## Related Documentation

- [guides/COMPONENT_DEVELOPMENT.md](guides/COMPONENT_DEVELOPMENT.en.md)
- [guides/CHAIN_ORCHESTRATION.md](guides/CHAIN_ORCHESTRATION.en.md)
- [GLOSSARY.md](reference/GLOSSARY.en.md)
- [GETTING_STARTED.md](GETTING_STARTED.en.md)
