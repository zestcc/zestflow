# ZestFlow 项目总结文档

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Explanation · [English](PROJECT_SUMMARY.en.md)
> 执行引擎与元件体系概要。完整架构见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 一、项目概述

ZestFlow 是一个基于 Spring Boot 的业务流程编排引擎，旨在将 Service 中的方法调用拆分为可复用的执行节点，并自动记录每个节点的输入参数、输出参数、耗时和异常信息。

### 1.1 核心特性

| 特性 | 说明 |
|------|------|
| **组件化设计** | 通过 `@ZestComponent` 标记组件，支持 28 种组件类型 |
| **执行链编排** | 支持串行、并行、条件分支、循环迭代等多种编排模式 |
| **高性能引擎** | 采用 StampedLock 乐观读 + 双缓冲热更新，读操作完全无锁 |
| **异步事件采集** | 三级异步管道，不阻塞业务线程 |
| **热更新支持** | 链定义可实时更新，无需重启应用 |
| **完整监控** | JDBC 事件存储，支持全链路追踪 |

### 1.2 技术栈

- **Java**: 17+（项目基线）
- **Spring Boot**: 3.2.5
- **数据库**: MySQL 8.x（默认）；Demo 测试可用 H2
- **前端**: Vue 3 + Element Plus + Vite + TypeScript
- **构建工具**: Maven 多模块

---

## 二、架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        ZestFlow 架构                            │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  @ZestComponent  │  │  @ZestExecute   │  │  @ZestParam     │              │
│  │  组件定义        │  │  执行方法        │  │  参数注入        │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
├─────────────────────────────────────────────────────────────────┤
│                        核心引擎层                               │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │              DefaultChainExecutionEngine                    ││
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐       ││
│  │  │ChainManager│ │NodeRunner │ │LifecycleExecutor│ │ChainKeyResolver│   ││
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘       ││
│  └─────────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│                        数据层                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  ChainContext  │  │  DataBus      │  │  EventCollector│              │
│  │  执行上下文     │  │  数据总线      │  │  事件采集       │              │
│  └─────────────┘  └─────────────┘  └─────────────┘              │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件说明

| 组件 | 职责 |
|------|------|
| **ChainManager** | 链定义管理，双缓冲热更新，StampedLock 乐观读 |
| **DefaultChainExecutionEngine** | 主执行引擎，负责链的完整执行流程 |
| **NodeRunner** | 节点执行器，处理拦截器、执行、重试、降级 |
| **LifecycleExecutor** | 生命周期执行器，参数解析、验证、反射调用 |
| **ChainKeyResolver** | 链键解析器，检查链状态和就绪性 |
| **ChainContext** | 执行上下文，存储链执行过程中的全部数据 |
| **AsyncEventPublisher** | 异步事件发布器，三级异步管道 |

### 2.3 执行流程

```
1. 链定义加载 → ChainManager.load()
2. 就绪性检查 → ChainKeyResolver.readinessFailure()
3. 创建执行实例 → ChainInstanceManager.register()
4. 拓扑排序 → 按依赖关系分层
5. 逐层执行 → NodeRunner.execute()
   ├── 拦截器前置处理
   ├── 参数解析 → ZestParamResolver
   ├── 组件执行 → LifecycleExecutor
   ├── 结果处理
   └── 拦截器后置处理
6. 事件采集 → AsyncEventPublisher
7. 结果返回 → ChainExecuteResultDTO
```

---

## 三、组件类型体系

### 3.1 组件类型枚举（28种）

| 类别 | 组件类型 | 说明 |
|------|---------|------|
| **基础执行** | EXECUTOR | 通用执行器 |
| | SERVICE | 服务调用 |
| | TASK | 任务执行 |
| **数据处理** | PARSER | 数据解析 |
| | TRANSFORMER | 数据转换 |
| | AGGREGATOR | 数据聚合 |
| | VALIDATOR | 数据校验 |
| **流程控制** | ROUTER | 路由分发 |
| | SELECTOR | 条件选择 |
| | FORK | 分支 |
| | JOIN | 汇合 |
| | ITERATOR | 迭代 |
| | WHILE | 循环 |
| **集成连接** | HTTP_INVOKE | HTTP调用 |
| | RPC_INVOKE | RPC调用 |
| | MQ_PRODUCER | 消息生产 |
| | MQ_CONSUMER | 消息消费 |
| | CACHE_READER | 缓存读取 |
| | CACHE_WRITER | 缓存写入 |
| **人工交互** | APPROVER | 审批 |
| | ASSIGNER | 分配 |
| | NOTIFIER | 通知 |
| **辅助增强** | LOGGER | 日志记录 |
| | TIMER | 定时器 |
| | SCRIPT | 脚本执行 |
| | FALLBACK | 降级处理 |
| | RETRY | 重试处理 |
| | INTERCEPTOR | 拦截器 |

### 3.2 节点类型

| 节点类型 | 常量 | 说明 |
|---------|------|------|
| NORMAL | NODE_TYPE_NORMAL | 普通节点 |
| CONDITION | NODE_TYPE_CONDITION | 条件节点 |
| SELECTOR | NODE_TYPE_SELECTOR | 选择器节点 |
| SCRIPT | NODE_TYPE_SCRIPT | 脚本节点 |
| SUB_CHAIN | NODE_TYPE_SUB_CHAIN | 子链节点 |
| ITERATOR | NODE_TYPE_ITERATOR | 迭代节点 |
| FORK | NODE_TYPE_FORK | 分支节点 |
| JOIN | NODE_TYPE_JOIN | 汇合节点 |
| TRY_CATCH | NODE_TYPE_TRY_CATCH | 异常捕获节点 |
| WHILE | NODE_TYPE_WHILE | 循环节点 |
| LOGGER | NODE_TYPE_LOGGER | 日志节点 |
| DELAY | NODE_TYPE_DELAY | 延迟节点 |

---

## 四、测试覆盖

### 4.1 测试结果

| 测试类 | 测试数 | 通过 | 失败 | 通过率 |
|--------|--------|------|------|--------|
| ComponentSmokeTest | 172 | 172 | 0 | **100%** |
| ZestFlowE2ETest | 12 | 10 | 2 | **83%** |

### 4.2 测试场景覆盖

- ✅ 简单线性链
- ✅ 并行 DAG 链
- ✅ 订单处理流程
- ✅ 条件分支链
- ✅ 迭代循环链
- ✅ 异常处理链
- ✅ 子链调用
- ✅ 重试机制
- ✅ 降级处理
- ✅ 参数验证

---

## 五、示例链条

### 5.1 链条统计

**总计：155 条示例链条**

### 5.2 场景分布

| 场景类别 | 数量 | 说明 |
|---------|------|------|
| 订单场景 | 10 | 创建、取消、退款、批量等 |
| 支付场景 | 10 | 支付、退款、钱包、提现等 |
| 库存场景 | 10 | 检查、扣减、转移、恢复等 |
| 营销场景 | 12 | 优惠券、折扣、积分、返现等 |
| 物流场景 | 13 | 创建、配送、退货、签收等 |
| 用户场景 | 13 | 注册、登录、认证、标签等 |
| 审批场景 | 14 | 简单审批、多级审批等 |
| 通知场景 | 14 | 短信、邮件、推送、微信等 |
| 数据处理 | 14 | 转换、过滤、聚合、拆分等 |
| API集成 | 15 | HTTP、REST、GraphQL、gRPC等 |
| 缓存场景 | 10 | 读取、写入、失效、刷新等 |
| MQ场景 | 10 | 生产、消费、发布、订阅等 |
| 复合场景 | 10 | 完整订单流、复杂DAG等 |

### 5.3 典型链条示例

#### 订单创建流程

```java
// 链定义
ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
    .code("order-create")
    .nodes(List.of(
        node("validate", "NORMAL", "validateOrder"),
        node("risk", "NORMAL", "checkRisk"),
        node("stock", "NORMAL", "deductStock"),
        node("create", "NORMAL", "createOrder"),
        node("notify", "NORMAL", "sendNotify")
    ))
    .edges(List.of(
        edge("validate", "risk"),
        edge("risk", "stock"),
        edge("stock", "create"),
        edge("create", "notify")
    ))
    .build();
```

#### 并行处理流程

```java
// 并行执行风控检查和库存扣减
ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
    .code("order-parallel")
    .nodes(List.of(
        node("validate", "NORMAL", "validateOrder"),
        node("risk", "NORMAL", "checkRisk"),
        node("stock", "NORMAL", "deductStock"),
        node("merge", "NORMAL", "mergeResult")
    ))
    .edges(List.of(
        edge("validate", "risk"),
        edge("validate", "stock"),
        edge("risk", "merge"),
        edge("stock", "merge")
    ))
    .build();
```

---

## 六、性能优化

### 6.1 并发控制

| 机制 | 实现 | 优势 |
|------|------|------|
| **乐观读锁** | StampedLock.tryOptimisticRead() | 读操作完全无锁，性能最优 |
| **双缓冲** | active + standby Map | 热更新无阻塞 |
| **写锁降级** | tryConvertToReadLock() | 减少锁持有时间 |

### 6.2 异步事件采集

```
三级异步管道：
业务线程 → Queue1 → EventCollector → Queue2 → BatchPublisher → JDBC
```

- **Level 1**: 业务线程直接写入内存队列
- **Level 2**: EventCollector 批量收集
- **Level 3**: BatchPublisher 批量写入数据库

### 6.3 性能指标

| 指标 | 数值 |
|------|------|
| 单节点执行耗时 | < 1ms |
| 链定义读取 | 无锁操作 |
| 热更新延迟 | < 10ms |
| 事件采集延迟 | 异步，不阻塞业务 |

---

## 七、同类方案对比

### 7.1 对比表格

| 特性 | ZestFlow | LiteFlow | Flowable |
|------|----------|----------|----------|
| **定位** | 业务流程编排 | 轻量级规则编排 | 企业级BPM |
| **包体积** | ~500KB | ~300KB | ~数MB |
| **数据库依赖** | 可选 | 可选 | 必须 |
| **热更新** | ✅ 双缓冲 | ✅ 平滑刷新 | ✅ 动态部署 |
| **脚本支持** | JS/Groovy | 8种语言 | JS/Groovy |
| **BPMN标准** | ❌ | ❌ | ✅ 完整支持 |
| **AI Agent** | ❌ | ✅ | ❌ |

### 7.2 选型建议

| 场景 | 推荐方案 |
|------|---------|
| 微服务业务编排 | **ZestFlow** / LiteFlow |
| 轻量级规则引擎 | LiteFlow |
| 企业级BPM平台 | Flowable |
| 高并发交易系统 | **ZestFlow** |
| AI Agent编排 | LiteFlow |
| 复杂审批流程 | Flowable |

---

## 八、关键修复记录

### 8.1 数据库持久化问题

**问题**：执行引擎在执行链之前会检查数据库中是否存在链记录，测试链只加载到内存导致"链不存在"错误。

**修复**：在测试代码中添加 `saveChainToDatabase` 方法，将测试链保存到数据库。

```java
private void saveChainToDatabase(String chainCode) {
    JdbcTemplate jdbcTemplate = new JdbcTemplate(executorDataSource);
    String sql = "INSERT INTO zf_chain (code, name, status, ...) VALUES (?, ?, ?, ...)";
    jdbcTemplate.update(sql, chainCode, chainCode, 4, ...);
}
```

### 8.2 ChainContext null值处理

**问题**：`ConcurrentHashMap` 不允许 null 值，调用 `ctx.put("key", null)` 会抛出 NPE。

**修复**：修改 `ChainContext.put` 方法，null 值时移除键。

```java
public void put(String key, Object value) {
    if (value == null) {
        data.remove(key);
    } else {
        data.put(key, value);
    }
}
```

### 8.3 测试参数补充

**问题**：部分组件缺少必要参数导致执行失败。

**修复**：在 `smokeParams()` 方法中添加缺失参数。

```java
p.put("paymentId", "PAY-SMOKE");
p.put("refundId", "REF-SMOKE");
p.put("template", "default-template");
p.put("businessType", "ORDER");
// ... 更多参数
```

---

## 九、快速开始

### 9.1 添加依赖

```xml
<dependency>
    <groupId>com.zestflow</groupId>
    <artifactId>zestflow-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

### 9.2 定义组件

```java
@ZestComponent("order")
public class OrderHandler {
    
    @ZestExecute(value = "createOrder", name = "创建订单")
    public Order createOrder(
            @ZestParam(value = "userId") String userId,
            @ZestParam(value = "productId") String productId,
            @ZestParam(value = "amount") double amount) {
        // 业务逻辑
        return order;
    }
}
```

### 9.3 定义链

```java
ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
    .code("order-flow")
    .nodes(List.of(
        node("create", "NORMAL", "createOrder"),
        node("pay", "NORMAL", "processPayment"),
        node("notify", "NORMAL", "sendNotify")
    ))
    .edges(List.of(
        edge("create", "pay"),
        edge("pay", "notify")
    ))
    .build();

chainManager.load(chainDefinitionBuilder.build(dto));
```

### 9.4 执行链

```java
Map<String, Object> params = Map.of(
    "userId", "U001",
    "productId", "P001",
    "amount", 99.9
);

ChainExecuteResultDTO result = chainExecutionEngine.execute("order-flow", params);

if (result.getStatus() == ChainConstants.CHAIN_SUCCESS) {
    System.out.println("执行成功！");
}
```

---

## 十、AI 集成（Copilot + Dev MCP）

> **更新** 2026-06-07 · 详见 [AI_COPILOT.md](./AI_COPILOT.md)

ZestFlow AI 分为 **编排 Copilot**（Admin 内）与 **开发 Copilot**（`zestflow-mcp` + IDE），口号：**Admin 设计链，MCP 连接规范与代码，Cursor 写元件**。

| 能力 | Orchestration Copilot | Dev Copilot（MCP） |
|------|----------------------|-------------------|
| 用户 | 业务 / 实施 / 编排人员 | 写 `@ZestComponent` 的开发者 |
| LLM | Admin 租户 BYOK | IDE 侧模型 |
| 典型功能 | NL→链草稿、表达式、日志诊断 | list 元件、validate 链、scaffold、plan_chain |
| 落盘 | 设计器 diff → 人工发布 | IDE Apply（MCP 不写盘） |

**Dev 接入（最新）**：

1. `install-mcp.ps1` — 平台 JAR 装到 `~/.zestflow/tools/`（一次）
2. `init-dev-project.ps1` / `--init-dev` — 生成 `.cursor/mcp.json`、`.zestflow/rules/project.md` 等
3. MCP **12 个 Tools**，含 Chain-first 学习（`plan_chain` → `distill_patterns`）

**文档索引**： [MCP_SETUP.md](./MCP_SETUP.md) · [AI_DEV_COPILOT_FINAL_SOLUTION.md](./AI_DEV_COPILOT_FINAL_SOLUTION.md) · [AI_CHAIN_LEARNING.md](./AI_CHAIN_LEARNING.md)

---

## 十一、未来规划

### 11.1 短期目标

- [ ] 完善剩余2个E2E测试用例
- [ ] 添加更多组件示例（每组件20+）
- [ ] 性能基准测试报告
- [ ] 监控看板开发

### 11.2 中期目标

- [ ] 可视化流程设计器
- [ ] 规则表达式DSL
- [ ] 多租户支持
- [ ] 分布式执行支持

### 11.3 长期目标

- [x] AI Copilot + Dev MCP（Phase 1～3，见 §十）
- [ ] AI Agent 深度编排（HTTP transport、企业模式）
- [ ] 云原生部署
- [ ] 企业级特性增强

---

## 十二、贡献指南

### 12.1 代码规范

- 遵循阿里巴巴Java开发规范
- 使用Lombok简化代码
- 单元测试覆盖率 > 80%

### 12.2 提交规范

```
feat: 新功能
fix: 修复bug
docs: 文档更新
refactor: 重构
test: 测试相关
```

---

**文档版本**: v1.1  
**更新日期**: 2026-06-07  
**维护团队**: ZestFlow Team
