# Annotations Reference

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](ANNOTATIONS.md)  
> **Source package:** `com.zestflow.executor.annotation`

---

## 1. Core Orchestration Annotations

### @ZestComponent

Marks a Spring Bean as a ZestFlow component container. Meta-annotated with `@Component`.

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | `""` | Component group code, e.g. `"order"` |

```java
@Component  // or implicitly registered via @ZestComponent
@ZestComponent("order")
public class OrderHandler { }
```

### @ZestExecute

Marks an orchestratable execution method. Corresponds to the "Executor" node bound in the designer.

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | `""` | Component ID; **defaults to method name when empty** |
| `name` | String | `""` | Display name in Admin |
| `description` | String | `""` | Description |
| `timeout` | long | `-1` | Milliseconds; `-1`=use node config, `0`=unlimited |
| `async` | boolean | `false` | Async execution (does not wait for result) |

**Globally unique ID convention:** Prefer `{componentCode}.{methodValue}`, e.g. `order.createOrder`.

```java
@ZestExecute(value = "createOrder", name = "Create Order", timeout = 30000)
public OrderCreatedResult createOrder(
        @ZestParam("userId") String userId,
        @ZestParam("amount") double amount) {
    return new OrderCreatedResult("ORD-001", amount);
}
```

### @ZestChain

Declares a chain placeholder in code; synced to the business database at startup (requires `declaration-sync-enabled=true`).

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | **Required** | chain_key |
| `name` | String | `""` | Display name |
| `description` | String | `""` | Description |

**Target:** `TYPE`, `METHOD`

### @ZestParam

Injects parameters from the chain context (DataBus).

| Attribute | Type | Default | Description |
|-----------|------|---------|-------------|
| `value` | String | `""` | DataBus key; empty = parameter name |
| `required` | boolean | `false` | Whether required |
| `defaultValue` | String | `""` | Default value (parsed as string) |
| `source` | String | `"databus"` | `databus` / `header` / `request` |
| `converter` | String | `""` | Custom `ParamConverter` Bean name |

---

## 2. Flow Control Annotations

| Annotation | Purpose | Key Attributes |
|------------|---------|----------------|
| `@ZestPredicate` | Conditional branch (CONDITION node) | `value`, `name` |
| `@ZestSelector` | Multi-way selection (SELECTOR node) | Used with `@ZestTag` |
| `@ZestParser` | Chain final-state parsing (PARSER) | — |
| `@ZestLoader` | Data loading | — |
| `@ZestTransformer` | Data transformation | — |
| `@ZestSplitter` | Data splitting | — |
| `@ZestFilter` | Data filtering | — |
| `@ZestAggregator` | Data aggregation | `strategy` defaults to `"ALL"` |
| `@ZestErrorHandler` | Chain / node error handling | — |

### @ZestTag / @ZestTags

Branch label mapping for `@ZestPredicate` / `@ZestSelector`.

```java
@ZestPredicate(value = "auditAfterSale", name = "After-Sale Audit")
@ZestTag(name = "Approve", value = "true")
@ZestTag(name = "Reject", value = "false")
public boolean auditAfterSale(@ZestParam("applyId") String applyId) {
    return true;
}
```

---

## 3. Lifecycle Annotations

| Annotation | Phase | Description |
|------------|-------|-------------|
| `@ZestPreProcessor` | Before execution | Interceptor chain |
| `@ZestPostProcessor` | After execution | Interceptor chain |
| `@ZestParamBinder` | Parameter binding | Custom binding logic |
| `@ZestParamValidator` | Parameter validation | Throws on validation failure |

---

## 4. Integration Annotations

| Annotation | Attributes | Default |
|------------|------------|---------|
| `@ZestHttpClient` | `method`, `url` | `GET`, `""` |
| `@ZestMqProducer` | `topic` | `""` |
| `@ZestMqConsumer` | `topic` | `""` |
| `@ZestCacheReader` | `cacheKey` | `""` |
| `@ZestCacheWriter` | `ttlSeconds` | `3600` |
| `@ZestLogger` | `level` | `"INFO"` |
| `@ZestDelay` | `delayMs` | `1000` |

---

## 5. Output and Context

| Annotation | Target | Description |
|------------|--------|-------------|
| `@ZestOutput("key")` | METHOD | Specifies the DataBus key for output |
| `@ZestResult` | PARAMETER | Marker; injects previous node result |
| `@ZestFailure` | PARAMETER | Marker; injects failure context |

You can also inject `ChainContext` directly:

```java
@ZestExecute("process")
public void process(ChainContext ctx) {
    ctx.put("key", value);
    String userId = ctx.get("userId", String.class);
}
```

---

## 6. Security

### @Sensitive

Masks fields during log / event collection.

| Attribute | Default | Description |
|-----------|---------|-------------|
| `strategy` | `MASK_ALL` | Masking strategy |
| `keepPrefix` / `keepSuffix` | `0` | Characters to preserve |

**MaskStrategy enum:** `MASK_ALL`, `MASK_MIDDLE`, `PREFIX_ONLY`, `SUFFIX_ONLY`, `PHONE`, `EMAIL`, `ID_CARD`

---

## 7. AI Component Generation

### @AiComponentGenerate (`component.ai` package)

| Attribute | Description |
|-----------|-------------|
| `description` | **Required** — component description |
| `inputKeys` | Input DataBus key array |
| `outputKey` | Output key |
| `category` | `BUSINESS` / `TRANSFORM` / `INTEGRATION` / `VALIDATION` / `FLOW_CONTROL` |

---

## 8. Important Notes

1. **Component classes must be Spring Beans** (`@Component` or implicit registration via `@ZestComponent`).
2. **`@ZestExecute` methods should be idempotent** — nodes may RETRY.
3. **Return values are collected in events** — use `@Sensitive` or avoid returning sensitive data.
4. **Do not encode flow in long if-else chains** — delegate to chain DAG orchestration.
5. After scanning, call `POST /api/components/refresh` to hot-update the component list.

---

## Related Documentation

- [guides/COMPONENT_DEVELOPMENT.md](../guides/COMPONENT_DEVELOPMENT.en.md) — Development guide
- [EXECUTION_ENGINE.en.md](EXECUTION_ENGINE.en.md) — Programmatic execution
- [API.en.md](API.en.md) — REST API reference
- [QUICK_REFERENCE.en.md](../QUICK_REFERENCE.en.md) — Quick reference tables
