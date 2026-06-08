# 快速参考

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](QUICK_REFERENCE.en.md)

本文提供高频查阅表；**完整说明**见 `docs/reference/` 专篇。

| 主题 | 详细文档 |
|------|---------|
| REST / Netty API | [API.md](API.md) |
| 注解全集 | [ANNOTATIONS.md](ANNOTATIONS.md) |
| 执行引擎 | [EXECUTION_ENGINE.md](EXECUTION_ENGINE.md) |
| SPI 扩展 | [SPI.md](SPI.md) |
| 配置项 | [CONFIGURATION.md](CONFIGURATION.md) |
| 常见问题 | [FAQ.md](FAQ.md) |

---

## 一、核心注解（摘要）

| 注解 | 用途 |
|------|------|
| `@ZestComponent("code")` | 元件组 |
| `@ZestExecute(value, name)` | 可编排方法 |
| `@ZestParam` | 参数注入 |
| `@ZestPredicate` | 条件分支 |
| `@ZestSelector` + `@ZestTag` | 多路选择 |
| `@ZestChain("key")` | 链占位声明 |

---

## 二、链 / 节点状态码

**链运行状态（`ChainExecuteResultDTO.status`）：**

| 值 | 常量 | 含义 |
|----|------|------|
| 0 | `CHAIN_INIT` | 初始化 |
| 1 | `CHAIN_LOADING` | 加载中 |
| 2 | `CHAIN_READY` | 就绪 |
| 3 | `CHAIN_RUNNING` | 执行中 |
| 4 | `CHAIN_SUCCESS` | 成功 |
| 5 | `CHAIN_FAILED` | 失败 |
| 6 | `CHAIN_TIMEOUT` | 超时 |
| 8 | `CHAIN_STOPPED` | 已停止 |

**节点状态（`NodeResultDTO.status`）：**

| 值 | 常量 | 含义 |
|----|------|------|
| 3 | `NODE_SUCCESS` | 成功 |
| 4 | `NODE_FAILED` | 失败 |
| 8 | `NODE_SKIPPED` | 跳过 |

---

## 三、节点类型（`ChainConstants.NODE_TYPE_*`）

| 常量 | 说明 |
|------|------|
| `NORMAL` | 普通执行器 |
| `CONDITION` | 条件 |
| `SELECTOR` | 选择器 |
| `SCRIPT` | Aviator 脚本 |
| `SUB_CHAIN` | 子链 |
| `FORK` / `JOIN` | 并行分支/汇合 |
| `WHILE` | 循环 |
| `ITERATOR` | 迭代 |
| `TRY_CATCH` | 异常捕获 |
| `DELAY` / `LOGGER` | 延迟 / 日志 |
| `HTTP_CLIENT` / `MQ_*` / `CACHE_*` | 集成型 |

---

## 四、错误策略

| 常量 | 行为 |
|------|------|
| `STOP` | 失败终止 |
| `CONTINUE` | 跳过继续 |
| `RETRY` | 自动重试 |

---

## 五、核心 API 速查

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

### HTTP 执行（Netty）

```bash
POST http://localhost:20550/execute
Content-Type: application/json

{"chainCode":"CHN...","params":{"userId":"U001"}}
```

### 链管理（Executor 侧）

| 类 | 方法 |
|----|------|
| `ChainLoader` | 启动加载、热 reload |
| `ChainRepository` | CRUD、版本 |
| `ChainInstanceManager` | 运行实例 |

---

## 六、端口

| 端口 | 组件 |
|------|------|
| 8080 | Admin |
| 8081 | 业务 Tomcat（Demo） |
| 20550 | Executor Netty |
| 20650 | Collector Netty |

---

## 七、最小配置

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

## 八、Maven 依赖

```xml
<dependency>
  <groupId>cn.zestflow.www</groupId>
  <artifactId>zestflow-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

---

## 相关文档

- [guides/COMPONENT_DEVELOPMENT.md](../guides/COMPONENT_DEVELOPMENT.md)
- [guides/CHAIN_ORCHESTRATION.md](../guides/CHAIN_ORCHESTRATION.md)
- [GLOSSARY.md](GLOSSARY.md)
