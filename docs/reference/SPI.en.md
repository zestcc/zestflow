# SPI Extension Points Reference

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Type** Reference · **Language** English · [简体中文](SPI.md)

ZestFlow uses SPI for pluggable design, comparable to Spring Boot AutoConfiguration.

---

## 1. EventCollector (Event Collection)

**Interface:** `com.zestflow.common.spi.EventCollector`  
**Modules:** `zestflow-common` (definition) → `collector-jdbc` / `kafka` / `rabbitmq` (implementations)

```java
void collect(ChainEvent event);
void collectBatch(List<ChainEvent> events);
String getName();
```

| Implementation | Module | Description |
|----------------|--------|-------------|
| `JdbcEventCollector` | collector-jdbc | Default; batch INSERT IGNORE |
| `KafkaEventCollector` | collector-kafka | Configure `zestflow.collector.kafka.topic` |
| `RabbitEventCollector` | collector-rabbitmq | Configure exchange / routingKey |

**Custom implementation:** Implement the interface and register as a Spring Bean; takes precedence over the default.

**Constraint:** `collect()` must return in ≤1ms; actual I/O runs in the async pipeline.

---

## 2. EventQueryService (Event Query)

**Interface:** `com.zestflow.collector.spi.EventQueryService`  
**Default implementation:** `JdbcEventQueryService` (collector-jdbc)

| Method | Description |
|--------|-------------|
| `queryEvents(EventQuery)` | Paginated events |
| `countEvents(EventQuery)` | Count |
| `getById(eventId)` | Single event |
| `queryStats(EventStatsQuery)` | Statistics |
| `queryExecutionTraces(EventQuery)` | Execution traces |
| `getExecutionTrace(executionId)` | Trace detail |
| `getNodeExecutionDetail(executionId, nodeId, nodeShape)` | Node input/output |
| `queryExecutionTrend` / `query*Ranking` / `queryFailureClusters` | Analytics APIs |

Admin `LogController` calls Collector Netty endpoints via `CollectorClient`.

---

## 3. ScheduleDriver (Schedule Driver)

**Interface:** `com.zestflow.common.spi.schedule.ScheduleDriver`

```java
String driverId();
void start();
void stop();
```

| Implementation | driverId | Description |
|----------------|----------|-------------|
| `EmbeddedScheduleDriver` | `embedded` | **Default**; reads business DB `zf_schedule` |
| `XxlJobScheduleDriver` | `xxl-job` | External xxl-job integration |

Switch driver:

```yaml
zestflow:
  executor:
    schedule:
      driver: embedded  # or xxl-job
```

See [adr/SCHEDULING_SPI_XXLJOB.md](../adr/SCHEDULING_SPI_XXLJOB.en.md).

---

## 4. Executor Extension Interfaces

| Interface | Package | Purpose |
|-----------|---------|---------|
| `ChainInterceptor` | `interceptor` | Chain-level before/after/onError |
| `NodeInterceptor` | `interceptor` | Node-level interception |
| `RetryPolicy` | `retry` | Custom retry policy |
| `FallbackStrategy` | `fallback` | Fallback logic |
| `ParameterResolver` | `param.resolver` | Parameter resolution extension |
| `ParamConverter` | `param` | Type conversion |
| `ChainMetricsSink` | `interceptor` | Metrics collection (default NOOP) |
| `EventPublisher` | `event` | Event publishing abstraction (`AsyncEventPublisher` implementation) |

### ChainInterceptor Example

```java
@Component
public class LoggingInterceptor implements ChainInterceptor {
    @Override
    public void beforeChain(ChainContext ctx) {
        log.info("Chain started chainCode={}", ctx.getChainCode());
    }
    @Override
    public int order() { return 100; }
}
```

---

## 5. InvocationPayloadService

**Interface:** `com.zestflow.collector.spi.InvocationPayloadService`  
**Purpose:** Persist / query large payloads (Playground responses, etc.)

| Method | Description |
|--------|-------------|
| `save(invocationId, payload)` | Persist |
| `getByInvocationId(id)` | Query |

---

## 6. Auto-Configuration

| Module | Trigger | Configuration Class |
|--------|---------|---------------------|
| executor | starter on classpath | `ExecutorAutoConfig` |
| collector-jdbc | classpath + datasource | `JdbcCollectorAutoConfig` |
| collector-kafka | `zestflow.collector.kafka.topic` configured | `KafkaCollectorAutoConfig` |
| collector-rabbitmq | `zestflow.collector.rabbitmq.exchange` configured | `RabbitCollectorAutoConfig` |

Import file: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## Related Documentation

- [ARCHITECTURE.md](../ARCHITECTURE.en.md) §12 — Extension point architecture
- [CONFIGURATION.en.md](CONFIGURATION.en.md) — SPI-related configuration
- [EXECUTION_ENGINE.en.md](EXECUTION_ENGINE.en.md) — Engine API
- [API.en.md](API.en.md) — Collector Netty endpoints
