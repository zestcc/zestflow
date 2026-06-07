# ZestFlow 快速参考指南

## 一、核心注解

| 注解 | 用途 | 示例 |
|------|------|------|
| `@ZestComponent` | 标记组件类 | `@ZestComponent("order")` |
| `@ZestExecute` | 标记执行方法 | `@ZestExecute(value = "create", name = "创建")` |
| `@ZestParam` | 参数注入 | `@ZestParam(value = "userId", required = true)` |
| `@ZestPredicate` | 条件判断方法 | `@ZestPredicate("checkCondition")` |
| `@ZestAggregator` | 结果聚合方法 | `@ZestAggregator("aggregateResults")` |
| `@ZestParamValidator` | 参数校验方法 | `@ZestParamValidator("validateParams")` |

## 二、节点类型常量

```java
// ChainConstants 中定义
NODE_TYPE_NORMAL     = "NORMAL";      // 普通节点
NODE_TYPE_CONDITION  = "CONDITION";   // 条件节点
NODE_TYPE_SELECTOR   = "SELECTOR";    // 选择器节点
NODE_TYPE_SCRIPT     = "SCRIPT";      // 脚本节点
NODE_TYPE_SUB_CHAIN  = "SUB_CHAIN";   // 子链节点
NODE_TYPE_ITERATOR   = "ITERATOR";    // 迭代节点
NODE_TYPE_FORK       = "FORK";        // 分支节点
NODE_TYPE_JOIN       = "JOIN";        // 汇合节点
NODE_TYPE_TRY_CATCH  = "TRY_CATCH";   // 异常捕获节点
NODE_TYPE_WHILE      = "WHILE";       // 循环节点
NODE_TYPE_LOGGER     = "LOGGER";      // 日志节点
NODE_TYPE_DELAY      = "DELAY";       // 延迟节点
```

## 三、链状态常量

```java
CHAIN_PENDING   = 1;  // 待执行
CHAIN_RUNNING   = 3;  // 执行中
CHAIN_SUCCESS   = 4;  // 执行成功
CHAIN_FAILED    = 5;  // 执行失败
CHAIN_CANCELLED = 6;  // 已取消
```

## 四、错误策略常量

```java
ERROR_STRATEGY_STOP     = "STOP";      // 停止执行
ERROR_STRATEGY_CONTINUE = "CONTINUE";  // 继续执行
ERROR_STRATEGY_RETRY    = "RETRY";     // 重试
```

## 五、核心API

### 5.1 ChainManager

```java
// 加载链定义
void load(ChainDefinition definition);

// 获取链定义
ChainDefinition get(String code);

// 批量获取
Map<String, ChainDefinition> getAll(Set<String> codes);

// 移除链定义
void remove(String code);
```

### 5.2 ChainExecutionEngine

```java
// 执行链
ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params);

// 异步执行链
CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Map<String, Object> params);
```

### 5.3 ChainContext

```java
// 获取值
<T> T get(String key, Class<T> type);

// 设置值
void put(String key, Object value);

// 获取或默认
<T> T getOrDefault(String key, Class<T> type, T defaultValue);

// 批量设置
void putAll(Map<String, Object> map);

// 是否包含键
boolean containsKey(String key);

// 移除键
void remove(String key);
```

### 5.4 ChainDefinitionBuilder

```java
// 构建链定义
ChainDefinition build(ChainDefinitionDTO dto);

// 从JSON构建
ChainDefinition buildFromJson(String json);

// 从YAML构建
ChainDefinition buildFromYaml(String yaml);
```

## 六、DTO结构

### 6.1 ChainDefinitionDTO

```java
@Builder
public class ChainDefinitionDTO {
    private String code;           // 链编码
    private int version;           // 版本号
    private List<ChainNodeDTO> nodes;   // 节点列表
    private List<ChainEdgeDTO> edges;   // 边列表
    private Map<String, Object> config; // 配置
}
```

### 6.2 ChainNodeDTO

```java
@Builder
public class ChainNodeDTO {
    private String id;           // 节点ID
    private String label;        // 节点标签
    private String type;         // 节点类型
    private String component;    // 组件ID
    private Map<String, Object> config; // 配置
    private String script;       // 脚本内容
    private String subChainCode; // 子链编码
}
```

### 6.3 ChainEdgeDTO

```java
@Builder
public class ChainEdgeDTO {
    private String source;    // 源节点ID
    private String target;    // 目标节点ID
    private String condition; // 条件表达式
    private String label;     // 边标签
}
```

### 6.4 ChainExecuteResultDTO

```java
public class ChainExecuteResultDTO {
    private String executionId;        // 执行ID
    private String chainCode;          // 链编码
    private int status;                // 执行状态
    private String errorMessage;       // 错误信息
    private String failedNodeId;       // 失败节点ID
    private List<NodeResultDTO> nodeResults; // 节点结果列表
    private long costMs;               // 总耗时
}
```

### 6.5 NodeResultDTO

```java
public class NodeResultDTO {
    private String nodeId;        // 节点ID
    private String componentId;   // 组件ID
    private int status;           // 执行状态
    private Object result;        // 执行结果
    private String errorMessage;  // 错误信息
    private long costMs;          // 耗时
    private int retryCount;       // 重试次数
}
```

## 七、配置项

### 7.1 application.yml

```yaml
zestflow:
  executor:
    # 数据源配置
    datasource:
      url: jdbc:mysql://localhost:3306/zestflow
      username: root
      password: password
    
    # 链重载配置
    chain:
      reload-polling-enabled: true
      reload-interval-ms: 5000
    
    # 异步事件配置
    event:
      async-enabled: true
      batch-size: 100
      flush-interval-ms: 1000
    
    # 线程池配置
    thread-pool:
      core-size: 10
      max-size: 50
      queue-capacity: 1000
```

## 八、数据库表结构

### 8.1 zf_chain（链定义表）

```sql
CREATE TABLE zf_chain (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128),
    description VARCHAR(512),
    status INT DEFAULT 1,
    version INT DEFAULT 1,
    tenant_id BIGINT,
    app_code VARCHAR(64),
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at DATETIME,
    updated_at DATETIME,
    is_deleted INT DEFAULT 0
);
```

### 8.2 zf_chain_event（链事件表）

```sql
CREATE TABLE zf_chain_event (
    event_id VARCHAR(64) PRIMARY KEY,
    event_type VARCHAR(32),
    execution_id VARCHAR(64),
    chain_id VARCHAR(64),
    chain_name VARCHAR(128),
    node_id VARCHAR(64),
    node_name VARCHAR(128),
    executor_id VARCHAR(128),
    app_code VARCHAR(64),
    app_name VARCHAR(64),
    tenant_id BIGINT,
    cost_ms INT,
    status INT,
    timestamp BIGINT,
    metadata TEXT,
    create_time DATETIME
);
```

### 8.3 zf_execution_payload（执行负载表）

```sql
CREATE TABLE zf_execution_payload (
    ref_id VARCHAR(64) PRIMARY KEY,
    ref_type VARCHAR(32),
    execution_id VARCHAR(64),
    source_type VARCHAR(32),
    scene_code VARCHAR(64),
    params TEXT,
    result TEXT,
    error_message TEXT,
    extra TEXT,
    tenant_id BIGINT,
    app_code VARCHAR(64),
    created_at DATETIME
);
```

## 九、常见问题

### Q1: 如何实现条件分支？

```java
// 使用 SELECTOR 节点
ChainNodeDTO selector = ChainNodeDTO.builder()
    .id("route")
    .type(ChainConstants.NODE_TYPE_SELECTOR)
    .component("routeSelector")
    .build();

// 边上添加条件
ChainEdgeDTO edge1 = ChainEdgeDTO.builder()
    .source("route")
    .target("nodeA")
    .condition("route == 'A'")
    .build();
```

### Q2: 如何实现并行执行？

```java
// 使用 FORK/JOIN 节点
nodes:
  - id: fork, type: FORK
  - id: taskA, type: NORMAL
  - id: taskB, type: NORMAL
  - id: join, type: JOIN

edges:
  - fork -> taskA
  - fork -> taskB
  - taskA -> join
  - taskB -> join
```

### Q3: 如何实现异常处理？

```java
// 使用 TRY_CATCH 节点
ChainNodeDTO tryCatch = ChainNodeDTO.builder()
    .id("tryCatch")
    .type(ChainConstants.NODE_TYPE_TRY_CATCH)
    .build();
```

### Q4: 如何实现重试？

```java
// 在节点配置中添加重试策略
Map<String, Object> config = Map.of(
    "retryCount", 3,
    "retryIntervalMs", 1000
);
ChainNodeDTO node = ChainNodeDTO.builder()
    .id("retryNode")
    .type(ChainConstants.NODE_TYPE_NORMAL)
    .component("retryComponent")
    .config(config)
    .build();
```

### Q5: 如何获取执行结果？

```java
ChainExecuteResultDTO result = chainExecutionEngine.execute("myChain", params);

// 检查执行状态
if (result.getStatus() == ChainConstants.CHAIN_SUCCESS) {
    // 获取节点结果
    for (NodeResultDTO nr : result.getNodeResults()) {
        System.out.println("节点: " + nr.getNodeId());
        System.out.println("结果: " + nr.getResult());
        System.out.println("耗时: " + nr.getCostMs() + "ms");
    }
}
```

---

## 六、AI 集成快速参考

> 完整设计见 [AI_COPILOT.md](./AI_COPILOT.md) · 安装见 [MCP_SETUP.md](./MCP_SETUP.md)

### 6.1 双 Copilot

| | Orchestration Copilot | Dev Copilot |
|--|----------------------|-------------|
| 载体 | Admin UI + 后端 | `zestflow-mcp.jar` + Cursor / Claude |
| LLM | Admin 租户配置（BYOK） | IDE 侧模型 |
| 落盘 | 设计器 diff → 人工发布 | IDE Apply（MCP 不写盘） |

### 6.2 Dev 接入（两步）

```powershell
powershell -File scripts/dev/install-mcp.ps1
powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot .
```

### 6.3 MCP Tools（12 个）

| Tool | 用途 |
|------|------|
| `list_components` | 元件白名单 |
| `read_project_file` | 读工程源码 |
| `validate_chain` | 链 JSON 校验 |
| `search_sources` | 关键词/glob 搜索 |
| `scaffold_component` | Java 脚手架（仅文本） |
| `export_task_package` | 导出任务包 Markdown |
| `plan_chain` | 意图 → 链规划 |
| `record_learning_event` | 记录学习事件 |
| `search_patterns` | 检索平台+项目 Pattern |
| `distill_patterns` | 事件蒸馏为 Pattern |
| `gen_playground_scene` | 生成 Playground 场景 |
| `share_pattern` | 导出 Pattern → Admin RAG |

### 6.4 推荐链式调用

```text
plan_chain → scaffold_component → validate_chain → gen_playground_scene
  → record_learning_event → distill_patterns
```

### 6.5 项目 Dev 目录

```text
.zestflow/rules/project.md      # L2 项目规则
.zestflow/patterns/             # L2 蒸馏 Pattern
.zestflow/learning/events.jsonl # L3 原始信号
.zestflow/mcp-audit.jsonl       # Tool 审计（可选关闭）
.cursor/mcp.json                # Cursor MCP 配置
```

### 6.6 Admin Copilot API（常用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/ai/copilot/suggest` | NL → 链草稿 |
| POST | `/api/ai/copilot/explain` | 解释当前链 |
| POST | `/api/ai/test` | 测试 LLM 连接 |
| POST | `/api/ai/learning/events` | 学习事件（Admin 侧） |

---

**版本**: v1.1  
**更新日期**: 2026-06-07
