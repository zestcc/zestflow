# Component Development Guide

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** How-to Guide · **Language** English · [简体中文](COMPONENT_DEVELOPMENT.md)

This guide explains how to write ZestFlow components in a Spring Boot business project, and how they are scanned, orchestrated, and executed in Admin.

---

## 1. Add the dependency

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

`zestflow-starter` bundles `zestflow-executor` + `collector-jdbc`. After adding it, the execution engine and JDBC event collector are auto-configured.

---

## 2. Core annotations

| Annotation | Scope | Description |
|------------|-------|-------------|
| `@ZestComponent("code")` | Class | Declares a component group; `code` is the component code prefix |
| `@ZestExecute(value, name)` | Method | Orchestrable execution node |
| `@ZestParam` | Parameter | Injects parameters from chain context |
| `@ZestPredicate` | Method | Condition evaluation (referenced by CONDITION nodes) |
| `@ZestAggregator` | Method | Result aggregation |
| `@ZestParamValidator` | Method | Input validation |

See [QUICK_REFERENCE.md](../QUICK_REFERENCE.en.md) for a full cheat sheet.

---

## 3. Basic example (from zestflow-demo)

```java
@Slf4j
@ZestComponent("order")
public class OrderHandler {

    @ZestExecute(value = "createOrder", name = "Create order")
    public OrderResults.OrderCreatedResult createOrder(
            @ZestParam(value = "userId", defaultValue = "U001") String userId,
            @ZestParam(value = "productId", defaultValue = "PROD-DEMO") String productId,
            @ZestParam(value = "quantity", defaultValue = "1") int quantity,
            @ZestParam(value = "amount", defaultValue = "99.9") double amount) {
        String orderId = "ORD-" + System.currentTimeMillis();
        log.info("Creating order userId={} orderId={}", userId, orderId);
        return new OrderResults.OrderCreatedResult(orderId, amount);
    }

    @ZestPredicate(value = "auditAfterSale", name = "After-sales audit")
    @ZestTag(name = "Approve", value = "true")
    public boolean auditAfterSale(@ZestParam("applyId") String applyId) {
        return true;
    }
}
```

**Try it (Netty):**

```bash
curl -X POST http://localhost:20550/execute \
  -H "Content-Type: application/json" \
  -d '{"chainCode":"YOUR_CHAIN_CODE","params":{"userId":"U001","amount":99.9}}'
```

Response fields: see [API.md](../reference/API.en.md) §2.1.

**Conventions:**

- Component classes must be Spring `@Component` beans (or registered via `@Configuration`)
- `@ZestExecute.value` must be globally unique; prefer `{componentCode}.{methodValue}` format
- Return values must be JSON-serializable and are written to chain context for downstream nodes

---

## 4. Parameter binding

During chain execution, JSON parameters from Admin / the designer map to `@ZestParam`:

```java
@ZestExecute(value = "ship", name = "Ship order")
public void ship(
        @ZestParam("orderId") String orderId,
        @ZestParam(value = "express", required = false, defaultValue = "SF") String express) {
    // ...
}
```

Context keys default to the `value` attribute. Upstream node outputs can be mapped to current node inputs via parameter binders configured in the designer.

---

## 5. Chain declaration (optional)

Use `@ZestChain` to declare chain placeholders in code; they sync to the business database at startup:

```java
@ZestChain(key = "orderFlow", name = "Order flow")
public class OrderChainDeclaration {}
```

With `zestflow.executor.chain.declaration-sync-enabled=true` (enabled by default), Admin shows the chain skeleton; complete the DAG in the designer.

---

## 6. Configuration essentials

```yaml
spring:
  application:
    name: my-shop          # Default moduleCode / appCode

zestflow:
  executor:
    admin-addresses: http://localhost:8080
    port: 20550            # Netty callback port
    access-token:          # Required in production; must match Admin
    registry-token:        # Registration / heartbeat token
    chain:
      auto-reload: true    # Hot reload chain definitions
```

Full configuration: [reference/CONFIGURATION.md](../reference/CONFIGURATION.en.md).

---

## 7. Verify your component

1. Start the business application; confirm `register success` in logs
2. Admin → **Component management**: scanned `@ZestExecute` methods should appear
3. In the **designer**, drag an Executor node and bind component + method
4. Test via **Playground** or `POST /execute` (Netty port 20550)

---

## 8. Best practices

| Practice | Reason |
|----------|--------|
| Single responsibility per method | Easier observability, retry, and hot swap |
| Avoid long if-else flows inside components | Express flow in chain orchestration instead |
| Return explicit DTOs, not raw Maps | Better log serialization and Copilot understanding |
| Mask sensitive fields before returning | Event collector records `result` |
| Design for idempotency | Nodes may retry (`ERROR_STRATEGY_RETRY`) |

---

## 9. Dev MCP assisted development

Connect `zestflow-mcp` in your IDE for component templates, lint checks, and chain intent hints. See [MCP_SETUP.md](../MCP_SETUP.en.md) and [AI_IDE_SETUP.md](../AI_IDE_SETUP.en.md) (Cursor / Claude / VS Code / Windsurf).

---

## Related documentation

- [CHAIN_ORCHESTRATION.en.md](CHAIN_ORCHESTRATION.en.md) — Orchestrate components in the designer
- [ARCHITECTURE.md](../ARCHITECTURE.en.md) §5.2 — Executor engine internals
- [QUICK_REFERENCE.md](../QUICK_REFERENCE.en.md) — API cheat sheet
