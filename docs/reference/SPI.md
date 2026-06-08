# SPI 扩展点参考

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](SPI.en.md)

ZestFlow 通过 SPI 实现可插拔设计，对标 Spring Boot AutoConfiguration。

---

## 1. EventCollector（事件采集）

**接口：** `com.zestflow.common.spi.EventCollector`  
**模块：** `zestflow-common`（定义）→ `collector-jdbc` / `kafka` / `rabbitmq`（实现）

```java
void collect(ChainEvent event);
void collectBatch(List<ChainEvent> events);
String getName();
```

| 实现 | 模块 | 说明 |
|------|------|------|
| `JdbcEventCollector` | collector-jdbc | 默认；批量 INSERT IGNORE |
| `KafkaEventCollector` | collector-kafka | 配置 `zestflow.collector.kafka.topic` |
| `RabbitEventCollector` | collector-rabbitmq | 配置 exchange / routingKey |

**自定义：** 实现接口并注册 Spring Bean，优先级高于默认实现。

**约束：** `collect()` 须 ≤1ms 返回；实际 IO 在异步流水线中完成。

---

## 2. EventQueryService（事件查询）

**接口：** `com.zestflow.collector.spi.EventQueryService`  
**默认实现：** `JdbcEventQueryService`（collector-jdbc）

| 方法 | 说明 |
|------|------|
| `queryEvents(EventQuery)` | 事件分页 |
| `countEvents(EventQuery)` | 计数 |
| `getById(eventId)` | 单条 |
| `queryStats(EventStatsQuery)` | 统计 |
| `queryExecutionTraces(EventQuery)` | 执行轨迹 |
| `getExecutionTrace(executionId)` | 轨迹详情 |
| `getNodeExecutionDetail(executionId, nodeId, nodeShape)` | 节点入参/出参 |
| `queryExecutionTrend` / `query*Ranking` / `queryFailureClusters` | 分析 API |

Admin `LogController` 通过 `CollectorClient` 调用 Collector Netty 端点。

---

## 3. ScheduleDriver（调度驱动）

**接口：** `com.zestflow.common.spi.schedule.ScheduleDriver`

```java
String driverId();
void start();
void stop();
```

| 实现 | driverId | 说明 |
|------|----------|------|
| `EmbeddedScheduleDriver` | `embedded` | **默认**；读业务库 `zf_schedule` |
| `XxlJobScheduleDriver` | `xxl-job` | 外部 xxl-job 集成 |

切换：

```yaml
zestflow:
  executor:
    schedule:
      driver: embedded  # 或 xxl-job
```

详见 [adr/SCHEDULING_SPI_XXLJOB.md](../adr/SCHEDULING_SPI_XXLJOB.md)。

---

## 4. Executor 扩展接口

| 接口 | 包 | 用途 |
|------|-----|------|
| `ChainInterceptor` | `interceptor` | 链级 before/after/onError |
| `NodeInterceptor` | `interceptor` | 节点级拦截 |
| `RetryPolicy` | `retry` | 自定义重试策略 |
| `FallbackStrategy` | `fallback` | 降级逻辑 |
| `ParameterResolver` | `param.resolver` | 参数解析扩展 |
| `ParamConverter` | `param` | 类型转换 |
| `ChainMetricsSink` | `interceptor` | 指标采集（default NOOP） |
| `EventPublisher` | `event` | 事件发布抽象（`AsyncEventPublisher` 实现） |

### ChainInterceptor 示例

```java
@Component
public class LoggingInterceptor implements ChainInterceptor {
    @Override
    public void beforeChain(ChainContext ctx) {
        log.info("链开始 chainCode={}", ctx.getChainCode());
    }
    @Override
    public int order() { return 100; }
}
```

---

## 5. InvocationPayloadService

**接口：** `com.zestflow.collector.spi.InvocationPayloadService`  
**用途：** 保存/查询大 payload（试验场响应等）

| 方法 | 说明 |
|------|------|
| `save(invocationId, payload)` | 持久化 |
| `getByInvocationId(id)` | 查询 |

---

## 6. 自动装配

| 模块 | 触发 | 配置类 |
|------|------|--------|
| executor | classpath 有 starter | `ExecutorAutoConfig` |
| collector-jdbc | classpath + datasource | `JdbcCollectorAutoConfig` |
| collector-kafka | `zestflow.collector.kafka.topic` 配置 | `KafkaCollectorAutoConfig` |
| collector-rabbitmq | `zestflow.collector.rabbitmq.exchange` | `RabbitCollectorAutoConfig` |

导入文件：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## 相关文档

- [ARCHITECTURE.md](../ARCHITECTURE.md) §12 — 扩展点架构图
- [CONFIGURATION.md](CONFIGURATION.md) — SPI 相关配置
- [EXECUTION_ENGINE.md](EXECUTION_ENGINE.md) — 引擎 API
