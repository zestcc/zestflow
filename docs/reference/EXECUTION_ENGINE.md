# 执行引擎参考

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](EXECUTION_ENGINE.en.md)
> **核心接口：** `com.zestflow.executor.engine.ChainExecutionEngine`

---

## 1. 概述

执行引擎负责：

1. 加载链定义（`ChainLoader` + `ChainRepository`）
2. DAG 拓扑排序与分层并行
3. 逐节点调用 `NodeRunner`（拦截器 → 参数解析 → 反射执行 → 事件发布）
4. 汇总为 `ChainExecuteResultDTO`

> **注意：** 文档中常说的「链管理」对应 `ChainLoader`、`ChainRepository`、`ChainInstanceManager`，**无 `ChainManager` 接口**。

---

## 2. ChainExecutionEngine API

### 2.1 execute（简单模式）

```java
ChainExecuteResultDTO execute(String chainCode, Object... args);
```

| 参数 | 说明 |
|------|------|
| `chainCode` | 已发布链编码 |
| `args` | 业务对象；引擎按**类型**注册到 DataBus，元件方法声明同类型参数即可注入 |

```java
OrderDTO order = new OrderDTO("U001", 99.9);
ChainExecuteResultDTO result = engine.execute("order-flow", order);
OrderCreatedResult created = result.getData(OrderCreatedResult.class);
```

### 2.2 execute（高级模式）

```java
ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args);
ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params,
                              Map<String, String> headers, Object... args);
```

| 参数 | 说明 |
|------|------|
| `params` | 显式 DataBus 键值（可为 null） |
| `headers` | HTTP 头透传（Mode 1/2） |
| `args` | 类型化业务对象 |

### 2.3 executeWithDeadline

```java
ChainExecuteResultDTO executeWithDeadline(String chainCode, Map<String, Object> params,
                                          long parentDeadlineMs);
```

子链节点继承父链绝对 deadline；`Long.MAX_VALUE` 表示无父约束。

### 2.4 executeAsync

```java
CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Object... args);
```

异步执行，适合长耗时链。

### 2.5 终止与查询

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `stop(instanceId)` | boolean | 终止单个实例 |
| `stopByChain(chainCode)` | int | 终止该链所有运行实例 |
| `listRunning(chainCode)` | List\<ChainInstance\> | 查询运行中实例 |

---

## 3. ChainExecuteResultDTO

| 方法 | 说明 |
|------|------|
| `isSuccess()` | `status == CHAIN_SUCCESS(4)` |
| `getData(Class<T>)` | 按类型取结果（优先 typedData → resultData） |
| `getReturnValue()` | 链终态返回值（PARSER 等） |
| `getReturnValue(Class<T>)` | 类型化终态返回值 |

| 字段 | 说明 |
|------|------|
| `instanceId` | 执行实例 ID |
| `nodeResults` | 各节点明细 |
| `resultData` | DataBus 快照 |
| `finalReturnValue` | 最后成功节点 returnValue |
| `failedNodeId` | 失败节点 |
| `errorCode` / `errorMessage` | 错误信息 |

---

## 4. ChainContext（执行上下文）

```java
<T> T get(String key, Class<T> type);
void put(String key, Object value);
<T> T getOrDefault(String key, Class<T> type, T defaultValue);
void putAll(Map<String, Object> map);
boolean containsKey(String key);
void remove(String key);
```

DataBus 在节点间传递数据；`@ZestParam` 从 DataBus 按 key 注入。

---

## 5. 链加载与热更新

| 类 | 职责 |
|----|------|
| `ChainLoader` | 启动加载、校验、热 reload |
| `ChainRepository` | 链 CRUD、版本快照 |
| `ChainInstanceManager` | 运行实例注册 |

**热更新：** `zestflow.executor.chain.auto-reload=true`（默认）时，Admin 发布或 PUT `/api/chains/{code}/reload` 触发 `ChainLoader` 双缓冲 reload，**无需重启**。

---

## 6. 错误策略

链/节点可配置：

| 策略 | 常量 | 行为 |
|------|------|------|
| 停止 | `STOP` | 失败即终止 |
| 继续 | `CONTINUE` | 跳过失败节点 |
| 重试 | `RETRY` | 按 `retryCount` / `retryIntervalMs` 重试 |

---

## 7. HTTP 双通道

| 通道 | 入口 | 响应 |
|------|------|------|
| **Netty** | `POST :20550/execute` | 固定完整 `ChainExecuteResultDTO` |
| **Tomcat** | `POST :8081/execute`（需 `execute-endpoint-enabled=true`） | 受 `execute-response-mode` 控制：BODY / DETAIL |

Admin 试验场、调度回调**必须**走 Netty 通道。

---

## 8. 事件发布

每个节点生命周期发射 `ChainEvent`（`CHAIN_STARTED`、`NODE_COMPLETED`、`NODE_FAILED` 等），经 `AsyncEventPublisher` 异步批量写入 Collector。

**原则：** 采集绝不阻塞业务线程（有界队列 + 熔断 + 可选磁盘降级）。

---

## 相关文档

- [API.md](API.md) — `/execute` 请求格式
- [ANNOTATIONS.md](ANNOTATIONS.md) — 元件注解
- [SPI.md](SPI.md) — EventCollector 扩展
- [ARCHITECTURE.md](../ARCHITECTURE.md) §5.2 — 引擎 internals
