# Frequently Asked Questions (FAQ)

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](FAQ.md)

---

## Getting Started and Deployment

### Q1: Admin fails to start with a Flyway error — what should I do?

**A:** For development databases, see [FLYWAY_POLICY.md](../FLYWAY_POLICY.en.md). Common fixes:

- `checksum mismatch` → automatic `repair()` on startup in non-prod environments
- Legacy Beta history drift → `FlywayLegacyHistoryCleaner` or `scripts/deploy/rebaseline-admin-dev.ps1`
- **Production:** automatic history cleanup is forbidden; requires DBA review

### Q2: Demo registration fails / Executor shows offline?

**A:** Checklist:

1. Admin is running (8080)
2. `zestflow.executor.admin-addresses=http://localhost:8080`
3. `registry-token` matches Admin `zestflow.admin.registry-token` (required in production)
4. Ports 20550 / 20650 are not in use

### Q3: Log page shows no data?

**A:**

1. Collector appears online in Admin **Collector Management**
2. Admin configured with `zestflow.collector.api-url` (default `http://localhost:20650`)
3. Run Playground or `/execute` first to generate events, then query
4. Confirm Demo has `zestflow.collector.registry.port=20650`

### Q4: Frontend changes don't take effect?

**A:** The frontend is embedded in the Admin jar. Run `pnpm build` in `zestflow-admin-ui/`; output goes to `zestflow-admin/src/main/resources/static/`.

---

## Chain Execution

### Q5: Chain published successfully but execution reports "chain not found"?

**A:**

1. Confirm the chain was **published** (not just design saved)
2. Target Executor is online and reload succeeded
3. `chainCode` matches the request; or use `chainKey` (takes precedence over chainCode)
4. Check `appCode` matches

### Q6: What's the difference between Netty `/execute` and Tomcat `/execute`?

**A:**

| | Netty :20550 | Tomcat :8081 |
|---|-------------|--------------|
| Response | **Fixed** full `ChainExecuteResultDTO` | Affected by `execute-response-mode` |
| Use case | Admin Playground, schedule callbacks | Business HTTP integration |
| Switch | Always available | `execute-endpoint-enabled=true` |

Admin Playground **must** use Netty. See [EXECUTION_ENGINE.en.md](EXECUTION_ENGINE.en.md) §7.

### Q7: Chain failed but HTTP returns 200?

**A:** This is **by design**. Netty `/execute` uses `ChainExecuteResultDTO.status` (`4`=success, `5`=failure) for structured parsing. Do not rely on HTTP status alone for business success/failure.

### Q8: How do I execute a chain programmatically?

**A:**

```java
@Autowired ChainExecutionEngine engine;

ChainExecuteResultDTO result = engine.execute("my-chain",
    Map.of("userId", "U001"),
    orderDto);
```

See [EXECUTION_ENGINE.en.md](EXECUTION_ENGINE.en.md).

---

## Component Development

### Q9: Admin component list is empty?

**A:**

1. Confirm class has `@ZestComponent` and is a Spring Bean
2. Method has `@ZestExecute`
3. Application is registered with Admin
4. Call `POST /api/components/refresh` or restart the application

### Q10: `@ZestParam` cannot resolve a value?

**A:**

1. Confirm `params` key matches `@ZestParam.value`
2. Check upstream node `outputData` / parameter binder configuration
3. When `required=true`, missing values throw; set `defaultValue` as fallback

### Q11: Node retry causes duplicate database writes?

**A:** Component methods should be **idempotent**. When node config uses `ERROR_STRATEGY_RETRY`, the method may be invoked multiple times.

---

## Scheduling

### Q12: Does cron still run when Admin is down?

**A:** **Yes.** Business cron is executed autonomously by Executor `EmbeddedScheduleDriver` reading business DB `zf_schedule` — it does not depend on Admin being online. Admin downtime affects: chain publishing, console schedule edits, log queries. See [adr/SCHEDULING.md](../adr/SCHEDULING.en.md).

### Q13: Do multiple Executor instances trigger cron jobs repeatedly?

**A:** Sharding is supported: `shardIndex` / `shardTotal` filter task ownership to avoid duplicates.

---

## Security and Production

### Q14: Minimum production configuration?

**A:**

- `--spring.profiles.active=prod`
- Change `jwt.secret` (≥32 characters)
- Configure three independent tokens: `registry-token`, `executor-access-token`, `collector.access-token`
- Expose only Admin :8080 via TLS on the public network; keep 20550/20650 internal
- See [DEPLOY.md](../DEPLOY.en.md)

### Q15: Does event collection affect business performance?

**A:** By design, **it should not**. `AsyncEventPublisher` uses a bounded queue + batch drain + circuit breaker; `offer()` timeout ≤1ms. Optional disk fallback when queue is full. Collection failures do not block chain execution.

---

## AI / MCP

### Q16: What's the difference between Copilot and Dev MCP?

**A:**

| | Admin Copilot | Dev MCP |
|---|--------------|---------|
| Scenario | Chain orchestration, expressions, diagnostics | Write Java components in IDE |
| Entry | Admin UI | Cursor / Claude Desktop |
| Module | zestflow-admin | zestflow-mcp.jar |

See [AI_COPILOT.md](../AI_COPILOT.en.md).

### Q17: MCP cannot connect to Executor?

**A:** Start MCP with `--executor-url=http://localhost:20550` (Netty port, not Tomcat 8081).

---

## Documentation and Contributing

### Q18: Documentation doesn't match the code — what should I do?

**A:** Open an issue on [Gitee Issues](https://gitee.com/zestcc/zestflow/issues) tagged `documentation`, or submit a PR per [DOCUMENTATION_MAINTENANCE.md](../DOCUMENTATION_MAINTENANCE.en.md). API and annotations are authoritative in `docs/reference/`; sync on release.

### Q19: How do I view the Admin OpenAPI specification?

**A:** Local Swagger UI: http://localhost:8080/swagger-ui.html; export: `scripts/docs/export-openapi.ps1`. See [OPENAPI.en.md](OPENAPI.en.md).

---

## Related Documentation

- [GETTING_STARTED.md](../GETTING_STARTED.en.md) — 30-minute getting started
- [API.en.md](API.en.md) — API reference
- [CONFIGURATION.en.md](CONFIGURATION.en.md) — Configuration reference
- [QUICK_REFERENCE.en.md](../QUICK_REFERENCE.en.md) — Quick reference tables
- [GLOSSARY.md](GLOSSARY.en.md) — Glossary
