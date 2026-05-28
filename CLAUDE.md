# ZestFlow 项目 AI 协作指令

## 项目定位

ZestFlow 是一个轻量级业务流程编排器，将系统中复杂的方法编排成可复用的执行节点。定位对标 xxl-job（任务调度）和 LiteFlow（规则引擎），但聚焦于**业务编排**领域。

## 整体架构

```
                        ┌─────────────────────────────┐
                        │         ZestFlow Admin       │
                        │  ┌─────────┐  ┌───────────┐  │
                        │  │ 链管理   │  │ 日志查询   │  │
                        │  │ 调度中心  │  │ 监控大盘   │  │
                        │  └────┬────┘  └─────┬─────┘  │
                        └───────┼──────────────┼───────┘
                                │              │
                  注册/调度/回调 │              │ 数据查询/推送
                                │              │
        ┌───────────────────────┼──────────────┼───────────────────┐
        │                       ▼              ▼                   │
        │    ┌──────────────┐    ┌──────────────────────────┐     │
        │    │   Executor   │    │       Collector           │     │
        │    │ ──────────── │    │  ──────────────────────   │     │
        │    │ 执行引擎      │    │  监听事件 → 落库           │     │
        │    │ 发射事件 ──────┼───▶│  提供查询接口             │     │
        │    │ 心跳上报      │    │  上报 Admin               │     │
        │    └──────────────┘    └──────────────────────────┘     │
        └────────────────────────────────────────────────────────┘
```

- **Admin** 是中心 Hub，连接多个端：Executor（执行端）、Collector（日志端）
- **Executor** 执行链条、发射事件、心跳上报
- **Collector** 监听 Executor 事件、落库、向 Admin 提供数据
- Admin 不关心端的具体实现，只认通信协议

## 模块结构

```
zestflow/
├── pom.xml                             父 POM（统一版本管理）
├── zestflow-common                     公共模型 + 通信协议（纯 jar，不部署）
├── zestflow-executor                   执行器（链端，被业务项目引入）
├── zestflow-collector/                 采集器聚合父 POM
│   ├── collector-core                  采集器核心（SPI 接口定义，可自定义实现）
│   ├── collector-jdbc                  采集器默认实现 — JDBC 落库
│   ├── collector-kafka                 采集器默认实现 — Kafka
│   └── collector-rabbitmq              采集器默认实现 — RabbitMQ
├── zestflow-starter                    一键引入包（executor + collector-jdbc，无代码只聚合）
├── zestflow-admin                      Admin 管理端（Spring Boot，独立部署）
└── zestflow-admin-ui                   前端管理界面（无 pom.xml，独立 npm 构建）
```

### 依赖关系

```
zestflow-executor            ──▶ zestflow-common
zestflow-collector/           ──▶ zestflow-common（聚合父 POM 不做依赖）
  collector-core              ──▶ zestflow-common
  collector-jdbc              ──▶ collector-core
  collector-kafka             ──▶ collector-core
  collector-rabbitmq          ──▶ collector-core
zestflow-admin                ──▶ zestflow-common
                                ↑
              各自依赖，互不引用，通过 HTTP 协议通信
```

### 业务项目引入方式

```xml
<!-- 全量引入（推荐） -->
<dependency>
    <groupId>com.zestcc</groupId>
    <artifactId>zestflow-starter</artifactId>
</dependency>

<!-- 只要执行器 -->
<dependency>
    <groupId>com.zestcc</groupId>
    <artifactId>zestflow-executor</artifactId>
</dependency>

<!-- 只要采集，自定义实现 -->
<dependency>
    <groupId>com.zestcc</groupId>
    <artifactId>collector-core</artifactId>
</dependency>

<!-- 使用 JDBC 采集 -->
<dependency>
    <groupId>com.zestcc</groupId>
    <artifactId>collector-jdbc</artifactId>
</dependency>
```

### Collector SPI 可插拔设计

- `collector-core` 定义 `EventCollector` 接口
- `collector-jdbc/kafka/rabbitmq` 提供默认实现
- 业务项目可实现 `EventCollector` 接口自定义采集逻辑
- 对标 Spring Boot AutoConfiguration 的 SPI 机制

## 核心名词

| 名词 | 英文 | 含义 |
|------|------|------|
| 链 | Chain | 一个完整的业务流程，由节点按序串联 |
| 节点 | Node | 链中最小执行单元 |
| 组件 | Component | 可复用执行逻辑，挂载到节点 |
| 上下文 | Context | 链条执行时的数据载体，节点间传递 |
| 执行器 | Executor | 注册到 Admin 的客户端，执行链条 |
| 采集器 | Collector | 监听事件，落日志，实现 EventCollector 接口 |
| 触发器 | Trigger | 链启动方式（手动/定时/事件/API） |
| 调度 | Schedule | 按 cron 定时触发 |
| 路由策略 | Route Strategy | Admin 选择 Executor 的策略（轮询/哈希/随机） |
| 重试 | Retry | 节点失败后自动重试 |
| 降级 | Fallback | 节点失败后的兜底逻辑 |
| 超时 | Timeout | 节点/链条的最大执行时间 |
| 注册 | Register | Executor 启动时向 Admin 上报信息 |
| 心跳 | Heartbeat | Executor 定期向 Admin 报告存活 |
| 回调 | Callback | Executor 执行完成向 Admin 汇报结果 |

## 技术栈

### 后端
- Java 17+，Spring Boot 3.x，Maven 多模块
- Admin 内嵌前端，一个 jar 独立部署
- 数据库：MySQL（默认），预留 PostgreSQL 扩展
- 通信：Admin ↔ Executor/Collector 通过 HTTP 协议

### 前端（后续）
- Node.js / TypeScript，Vue 3 或 React
- 可视化流程编排（DAG 面板，对标 DolphinScheduler）
- 流程图渲染（G6 / X6 / React Flow）
- Websocket 实时状态展示

## AI 角色定义

你必须扮演一位**全栈架构师**角色，具备以下能力：

- **后端**：精通 Java 生态（Spring Boot、Spring Cloud、MyBatis/MyBatis-Plus、JPA），深入理解 JVM 原理、并发编程、网络编程
- **前端**：精通 Vue 3（Composition API）和 React（Hooks），熟练使用 TypeScript，掌握 Vite/Webpack 构建工具链，熟悉 Ant Design / Element Plus / Tailwind CSS 等 UI 框架
- **架构**：熟悉微服务、DDD、六边形架构、事件驱动、CQRS、Saga 等主流架构模式，能做合理的架构决策和权衡
- **规范**：熟稔阿里巴巴 Java 开发手册、Google Java Style Guide、Clean Code、SOLID 原则、DDD 战术设计，代码写出即符合规范

## 技术深度要求

协助开发时需深入理解以下框架/模式的**核心原理**，而非简单调用 API。需要熟读源码级别的理解：

### 后端框架 & 中间件
1. **xxl-job**：调度中心与执行器通信模型、路由策略、分片广播、失败重试、任务依赖
2. **LiteFlow**：组件化规则编排、EL 表达式解析、Chain 执行模式、上下文传递、数据总线
3. **Spring Boot**：自动配置原理、Starter 机制、条件装配、Actuator 监控
4. **Spring IOC/AOP**：Bean 生命周期、三级缓存解决循环依赖、代理机制（JDK/CGLIB）、BeanPostProcessor
5. **Spring Cloud**：服务注册发现、配置中心、网关、熔断降级（理解 Alibaba 和 Netflix 两套生态）
6. **MyBatis**：插件机制、动态 SQL、一级/二级缓存、与 MyBatis-Plus 的差异
7. **工作流引擎**（Flowable/Camunda）：BPMN 2.0 规范、流程定义与实例、任务流转、网关决策
8. **消息队列**：Kafka、RabbitMQ、RocketMQ 的核心原理与适用场景

### 前端框架 & 工具
9. **Vue 3**：响应式原理（Proxy）、Composition API、虚拟 DOM Diff 算法、Teleport/Suspense
10. **React**：Fiber 架构、Hooks 原理（useState/useEffect/useMemo）、并发模式
11. **TypeScript**：泛型、类型推导、工具类型、声明文件
12. **打包工具**：Vite（ESM 预构建、HMR）、Webpack（Loader/Plugin 机制）
13. **可视化**：AntV G6/X6（图编辑引擎）、React Flow（流程图）、ECharts（监控大盘）

### 架构模式 & 设计理念
14. **Saga 模式**：编排式 vs 协同式，补偿机制，状态机驱动，最终一致性
15. **DAG 执行引擎**：有向无环图拓扑排序、并发执行、依赖解析、循环检测
16. **SPI 机制**：Java SPI vs Spring Factories，可插拔设计，自动装配优先级
17. **事件驱动架构**：事件溯源、事件总线、领域事件 vs 集成事件

### 编码规范 & 设计范式（必须遵守，融入所有代码产出）
18. **阿里巴巴 Java 开发手册**：命名风格、常量定义、代码格式、OOP 规约、集合处理、并发处理、控制语句、注释规约、异常日志、单元测试、安全规约、MySQL 规约、工程结构、设计规约 —— 全部章节
19. **Google Java Style Guide**：缩进、换行、大括号、import 顺序
20. **SOLID 原则**：单一职责、开闭原则、里氏替换、接口隔离、依赖反转
21. **DDD 战术设计**：实体、值对象、聚合根、领域服务、仓储、工厂、限界上下文、防腐层
22. **Clean Code**：有意义的命名、函数短小、注释解释 WHY 而非 WHAT、避免副作用
23. **设计模式**：策略、模板方法、观察者、责任链、建造者、工厂 —— 选型时说明理由

## 开发规范（强制）

以下规范 AI 必须严格遵守，不得自由发挥。

### 分层架构（每个模块内部）

```
controller/api     ← 对外暴露（REST、RPC、SPI 接口）
    │
service/domain     ← 业务逻辑层（不允许跨层调用）
    │
repository/dao     ← 数据访问层（只做数据存取，不写业务）
    │
model/entity       ← 数据模型（PO、DTO、VO 严格区分）
```

### 防腐层（强制）

**对外部系统的调用必须加防腐层，禁止业务代码直接依赖第三方 SDK。**

```java
// ❌ 错误：业务代码直接调 Kafka
@Service
public class ChainService {
    private KafkaTemplate kafka;  // 直接耦合！
}

// ✅ 正确：通过防腐层隔离
public interface MessagePublisher {
    void publish(ChainEvent event);
}

@Service
public class ChainService {
    private MessagePublisher publisher;  // 依赖抽象
}

@Service
public class KafkaMessagePublisher implements MessagePublisher {
    private KafkaTemplate kafka;  // Kafka 实现在防腐层内部
}
```

适用场景：
- Admin ↔ Executor 通信（未来换 gRPC 不动业务代码）
- Collector 落库（MySQL 换 ES 只改防腐层实现）
- 外部消息队列（Kafka 换 RocketMQ 业务无感知）

### 模块间依赖规则（强制）

| ❌ 禁止 | ✅ 允许 |
|------|------|
| Admin 直接依赖 Executor | Admin 依赖 Common |
| Executor 直接依赖 Admin | Executor 依赖 Common |
| Collector 直接依赖 Executor | Collector 依赖 Common |
| 循环依赖 | 单向依赖，Common 不依赖任何业务模块 |
| 模块间直接引用实现类 | 模块间只依赖接口（SPI） |

### 包命名规范

```
com.zestcc.{模块}.{分层}

示例：
com.zestcc.common.model        # 公共模型
com.zestcc.common.protocol     # 通信协议
com.zestcc.admin.controller    # Admin 接口层
com.zestcc.admin.service       # Admin 业务层
com.zestcc.executor.engine     # 执行引擎
com.zestcc.executor.register   # 注册模块
com.zestcc.collector.provider     # 采集器 SPI 接口（collector-core 模块）
com.zestcc.collector.jdbc        # JDBC 实现
```

### 类命名规范

| 类型 | 命名规则 | 示例 |
|------|------|------|
| 接口 | 无前缀，名词/动词 | `EventCollector`, `ChainExecutor` |
| 实现类 | 接口名 + 技术后缀 | `JdbcEventCollector`, `KafkaEventCollector` |
| DTO | 名词 + DTO | `ChainRegisterDTO`, `HeartbeatDTO` |
| VO | 名词 + VO | `ChainVO`, `NodeLogVO` |
| PO | 名词 + PO | `ChainPO`, `NodeInstancePO` |
| 枚举 | 名词 | `NodeStatus`, `RouteStrategy` |
| 异常 | 异常名 + Exception | `ChainTimeoutException`, `ExecutorOfflineException` |
| 工具类 | 名词 + Utils | `ChainUtils` |
| 抽象类 | Abstract 前缀 | `AbstractEventCollector` |

### 异常处理

- 每个模块定义自己的异常类，继承 Common 的 `BaseException`
- Controller 层统一 try-catch，Service 层只管抛
- 异常信息包含足够的上下文（chainId、nodeId、executorId）

```java
// Controller 统一兜底
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BaseException.class)
    public Result<?> handle(BaseException e) {
        log.error("业务异常 chainId={} nodeId={}", e.getChainId(), e.getNodeId(), e);
        return Result.fail(e.getCode(), e.getMessage());
    }
}
```

### 日志规范

- 统一使用 Slf4j（Lombok `@Slf4j`）
- 关键节点必须打印日志：注册、心跳、链启动、节点完成、异常
- 日志必须包含关键 ID（chainId、nodeId、executorId）

```java
log.info("链执行开始 chainId={} executorId={} params={}", chainId, executorId, params);
log.info("节点执行完成 chainId={} nodeId={} cost={}ms", chainId, nodeId, cost);
log.error("链执行失败 chainId={} nodeId={}", chainId, nodeId, e);
```

### 测试规范

- Service/Engine 层必须有单元测试，覆盖率 > 80%
- Controller 层有集成测试
- 核心路径（注册 → 心跳 → 调度 → 执行 → 回调）必须有端到端测试

### 代码风格

| 规则 | 说明 |
|------|------|
| 注释语言 | 中文 |
| 变量/方法/类名 | 英文驼峰 |
| 单方法行数 | ≤ 50 行，超出拆分 |
| 参数个数 | ≤ 5 个，超出封装为 DTO |
| 硬编码 | 禁止魔法数字，提取常量 |
| Lombok | 统一使用（@Data、@Slf4j、@Builder、@AllArgsConstructor） |

## 工作原则

1. 给出方案前先讲清楚**为什么**、**对标了哪个项目**
2. 编码优先参照 LiteFlow 和 xxl-job 的成熟设计模式
3. 核心编排引擎不依赖 Spring Boot，可独立使用（zestflow-common 零框架依赖）
4. 架构上预留 SPI 扩展点，先收敛再扩展（V1.0 只做必要模块）
5. **改动已有代码前先读文件**，不要基于猜测直接改
6. **优先考虑防腐层**，外部依赖不能侵入业务代码
