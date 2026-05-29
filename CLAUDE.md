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
zestflow-executor            ──▶ zestflow-common, collector-core（调用 EventCollector SPI 发射事件）
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
    <groupId>com.zestflow</groupId>
    <artifactId>zestflow-starter</artifactId>
</dependency>

<!-- 只要执行器 -->
<dependency>
    <groupId>com.zestflow</groupId>
    <artifactId>zestflow-executor</artifactId>
</dependency>

<!-- 只要采集，自定义实现 -->
<dependency>
    <groupId>com.zestflow</groupId>
    <artifactId>collector-core</artifactId>
</dependency>

<!-- 使用 JDBC 采集 -->
<dependency>
    <groupId>com.zestflow</groupId>
    <artifactId>collector-jdbc</artifactId>
</dependency>
```

### Collector SPI 可插拔设计

- `collector-core` 定义 `EventCollector` 接口
- `collector-jdbc/kafka/rabbitmq` 提供默认实现
- 业务项目可实现 `EventCollector` 接口自定义采集逻辑
- 对标 Spring Boot AutoConfiguration 的 SPI 机制

## POM 规范（强制）

以下为本项目 Maven 项目结构和版本管理的约定规则。

### 组织与模块

| 项目 | 值 |
|------|------|
| groupId | `com.zestflow` |
| 父 POM | `com.zestflow:zestflow:1.0.0-SNAPSHOT` |
| 前端项目 | `zestflow-admin-ui` 无 pom.xml，独立 npm 构建 |

### 模块版本管理

**每个模块拥有独立版本属性**，统一在父 POM 的 `<properties>` 中声明：

```xml
<properties>
    <zestflow-common.version>1.0.0-SNAPSHOT</zestflow-common.version>
    <zestflow-executor.version>1.0.0-SNAPSHOT</zestflow-executor.version>
    <collector-core.version>1.0.0-SNAPSHOT</collector-core.version>
    <collector-jdbc.version>1.0.0-SNAPSHOT</collector-jdbc.version>
    <collector-kafka.version>1.0.0-SNAPSHOT</collector-kafka.version>
    <collector-rabbitmq.version>1.0.0-SNAPSHOT</collector-rabbitmq.version>
    <zestflow-starter.version>1.0.0-SNAPSHOT</zestflow-starter.version>
    <zestflow-admin.version>1.0.0-SNAPSHOT</zestflow-admin.version>
</properties>
```

**子模块引用规则：**

| 模块层级 | 版本写法 | 示例 |
|---------|---------|------|
| 叶子模块（有代码产出） | 表达式 `${xxx.version}` | `<version>${zestflow-common.version}</version>` |
| 中间聚合 POM | 字面量（继承父版本） | `<version>1.0.0-SNAPSHOT</version>` |

- 中间聚合 POM（如 `zestflow-collector`）不独立发版，用字面量避免 Maven 孙模块解析失败
- 发版时只需改父 POM 中对应属性，不影响其他模块

### 依赖管理

**父 POM 职责：**

```xml
<!-- dependencyManagement：只定义版本，不引入依赖 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.zestflow</groupId>
            <artifactId>zestflow-common</artifactId>
            <version>${zestflow-common.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- dependencies：推给所有子模块的基础依赖 -->
<dependencies>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>           <!-- provided 作用域，编译期无运行时耦合 -->
    </dependency>
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>        <!-- 纯接口，不绑定实现 -->
    </dependency>
</dependencies>
```

**子模块职责：** 显式声明自己的业务依赖和 JUnit（不继承 root 的 JUnit），Lombok 和 Slf4j 由父 POM 统一提供。

### 构建规范

- **不使用** `flatten-maven-plugin`（不产生 `.flattened-pom.xml`）
- Maven 编译时 `version contains an expression` 警告属于正常，不影响构建
- `zestflow-common` 零第三方框架依赖，仅 Lombok + Slf4j

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

### 前端
- **技术栈**：Vue 3 (Composition API + `<script setup>`) + Element Plus + Vite + TypeScript + vue-i18n
- **状态管理**：Pinia
- **路由**：Vue Router 4.x（导航守卫控制登录态）
- **HTTP 客户端**：Axios（拦截器统一处理 JWT token / 异常）
- **构建**：Vite 5.x，ESLint + Prettier
- **可视化（后续）**：Vue Flow / X6 实现 DAG 流程编排面板
- **实时（后续）**：WebSocket 展示执行状态

### 前端开发环境要求

| 依赖 | 版本要求 | 用途 |
|------|---------|------|
| Node.js | 18.x 或 20.x LTS | 运行时 |
| npm / pnpm | 9+ / 8+ | 包管理 |
| 浏览器 | Chrome / Edge 最新版 | 开发调试 |

推荐使用 pnpm 作为包管理器（速度快、节省磁盘）。

### 前端项目规范

```
zestflow-admin-ui/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── index.html
└── src/
    ├── main.ts                 # 入口
    ├── App.vue                 # 根组件
    ├── api/                    # API 防腐层（Axios 实例 + 模块化 API）
    ├── router/                 # 路由配置 + 导航守卫
    ├── stores/                 # Pinia 状态管理
    │   ├── user.ts             #   用户登录态
    │   └── app.ts              #   布局状态
    ├── views/                  # 页面
    │   ├── login/              #   登录
    │   ├── register/           #   注册
    │   ├── forgot/             #   找回密码
    │   ├── dashboard/          #   仪表盘
    │   ├── chains/             #   链管理
    │   ├── schedules/          #   调度中心
    │   ├── logs/               #   日志查询
    │   └── settings/           #   系统设置
    ├── layout/                 # 布局组件
    │   └── AppLayout.vue       #   侧边栏 + 顶栏 + 内容区
    └── components/             # 通用组件
```

### 认证流程

1. 用户登录 → 后端返回 JWT token
2. 前端存 token 到 `localStorage` + Pinia
3. Axios 拦截器自动在请求头携带 `Authorization: Bearer <token>`
4. 服务端返回 401 → 清除 token → 跳转登录页
5. 页面刷新 → 从 `localStorage` 恢复 token 到 Pinia

### 国际化规范（强制）

**所有前端 UI 文本必须通过 vue-i18n 翻译，禁止硬编码。**

技术选型：

| 层 | 技术 |
|------|------|
| 框架 | vue-i18n 9.x (Composition API 模式) |
| 默认语言 | zh-CN |
| 备用语言 | en |
| Element Plus 联动 | 通过 `el-config-provider` 动态切换 locale |

目录结构：

```
src/i18n/
├── index.ts              # createI18n 实例（legacy: false）
├── useLocale.ts          # 语言切换 composable（同步 vue-i18n + Element Plus locale）
└── locales/
    ├── zh-CN.ts          # 简体中文
    └── en.ts             # English
```

使用规范：

| 场景 | 方式 | 示例 |
|------|------|------|
| 模板中 | `$t('key')` | `{{ $t('login.title') }}` |
| 脚本中 | `t()` from `useI18n()` | `const { t } = useI18n()` |
| 非组件文件 | `i18n.global.t()` | `import i18n from '@/i18n'` |
| 表单验证消息 | 函数形式（响应式） | `message: () => t('login.usernameRequired')` |
| Element Plus 错误消息 | 写入 i18n locale | `ElMessage.error(t('common.networkError'))` |

翻译 key 命名规范：
- 按页面/模块命名空间组织：`login.xxx`, `register.xxx`, `dashboard.xxx`
- 通用词汇放在 `common.xxx` 下复用
- 验证消息放在 `validation.xxx` 下
- 布局相关放在 `layout.xxx` 下

语言切换：
- `AppHeader.vue` 中提供中/英文切换按钮
- 用户选择保存在 `localStorage`（key: `locale`），刷新后保持
- 切换时同步更新 Element Plus 组件语言

### V1.0 前端页面清单

| 路径 | 页面 | 访问限制 |
|------|------|---------|
| `/login` | 登录 | 未登录 |
| `/register` | 注册 | 未登录 |
| `/forgot` | 找回密码 | 未登录 |
| `/dashboard` | 仪表盘 | 需登录 |
| `/chains` | 链列表 | 需登录 |
| `/chains/create` | 新建链 | 需登录 |
| `/chains/:id` | 链详情/节点编排 | 需登录 |
| `/schedules` | 调度列表 | 需登录 |
| `/logs` | 日志查询 | 需登录 |
| `/settings` | 系统设置 | 需登录 |

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
com.zestflow.{模块}.{分层}

示例：
com.zestflow.common.model        # 公共模型
com.zestflow.common.protocol     # 通信协议
com.zestflow.admin.controller    # Admin 接口层
com.zestflow.admin.service       # Admin 业务层
com.zestflow.executor.engine     # 执行引擎
com.zestflow.executor.register   # 注册模块
com.zestflow.collector.spi          # 采集器 SPI 接口（collector-core 模块）
com.zestflow.collector.jdbc        # JDBC 实现
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

### 前端 UI 规范（强制）

1. **禁止自定义 CSS 修饰表格和表单** — 能用 Element Plus 属性/参数实现的，一律不写自定义 CSS
2. **表格统一表头样式** — 所有 `el-table` 统一使用 `:header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"`
3. **列宽适配** — 不写固定 px 列宽，让 Element Plus 自动分配，操作列可单独设 `width` 防止按钮溢出
4. **表格分页** — 所有列表页必须加分页组件 `el-pagination`
5. **筛选条件** — 列表页顶部加筛选栏，查询/重置按钮
6. **输入框样式** — 不改动 Element Plus 输入框默认样式，登录/注册/找回密码页的反自动填充 CSS（`inset box-shadow`）是系统级防护不可删除
7. **内容溢出** — 可能超长的列加 `show-overflow-tooltip`，气泡展示完整内容
8. **弹窗尺寸** — 弹窗宽度要充足（如 1200px），确保内容不挤占；表格列宽分配均匀，避免某列过长或过短
9. **统计信息** — 列表页顶部展示分类统计（全部/正常/异常/离线），用不同颜色区分
10. **禁止交互元素文本选中** — 按钮、菜单、标签等交互元素设置 `user-select: none`，写在 `index.html` 的全局 `<style>` 中确保生效

### 开发环境规范

1. **端口管理** — 重启开发服务器时，必须先杀掉旧进程再在原端口启动，不得自动换端口。Windows Git Bash 下需用 `//F` 而非 `/F` 避免 MSYS 路径转换（`/F` 会被转为 `F:/`）。
2. **默认端口一览** — Admin（8080）、Executor Netty 回调（9999，`zestflow.executor.port` 可配）、Executor 测试应用（8081）

### 数据库变更规范（强制）

1. **所有数据库改动必须入 init.sql** — 任何 DDL（新增表、新增字段、修改字段、索引变更等）都必须在 `init.sql` 中体现，禁止只在数据库上执行而不同步脚本。
2. **注释带时间戳** — 所有 DDL 修改的注释上方必须加上当前日期标记，格式为 `-- YYYY-MM-DD：修改内容说明`，方便追溯变更历史。
3. **DDL 脚本 + 实时库同步** — 修改 `init.sql` 后，必须直接在数据库上执行对应的 `ALTER TABLE` 语句，保证实时库和脚本一致。仅改 SQL 文件会导致运行时报 `Unknown column` 错误。
4. **默认值双重保障** — 新增字段的默认值同时在 DDL（`DEFAULT xxx`）和 Service 层代码中设置，避免空指针。

## 已实现功能

### 强制改密流程（2026-05）

**需求：** 管理员创建/重置用户时自动生成密码，用户首次登录须强制修改密码后重新登录。自注册用户无需强制改密。

**实现要点：**
- 后端：`UserPO.mustChangePassword` 字段（0/1），`init.sql` 中 `DEFAULT 1`
- 密码生成：`SecureRandom` 12位（去歧义字符集 `ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789`），BCrypt 加密落库
- 新建用户 / 重置密码 → 自动生成密码 → `mustChangePassword=1` → 弹窗展示明文密码（关闭后不可再次查看）
- 登录时检测 `mustChangePassword=1` → 跳转 `/force-password` → 修改成功后清除标记 → 跳回登录页（用户名回显，密码清空）
- 新密码不能与当前密码相同（`passwordEncoder.matches()` 校验）
- 前台路由 `/force-password` + 导航守卫拦截

### 用户管理页约定

- 列表页顶部统计，筛选栏（用户名/邮箱/状态/超管），分页
- 已分配模块列：`{{ row.moduleRoles?.length || 0 }}`
- 分配模块弹窗：用户名 + 邮箱标签（`v-if` 邮箱为空时不展示），模块角色表格
- 创建/重置成功弹窗显示明文密码，仅一次机会查看

### 执行器自动注册 + 心跳检测（2026-05）

**需求：** Executor 启动时自动注册到 Admin，定时发送心跳，Admin 检测离线执行器。对标 xxl-job 注册模型 + Nacos 退避策略。

**协议层（`zestflow-common`）：**
- `common.model.dto.RegisterDTO` — 注册请求（executorId, host, port, moduleCode, moduleName）
- `common.model.dto.HeartbeatDTO` — 心跳请求（executorId）
- `common.constant.RegistryConstants` — 默认心跳间隔 30s，死亡超时倍数 3x，常量 `STATUS_ONLINE=1`/`STATUS_OFFLINE=0`/`STATUS_ABNORMAL=2`
- `zestflow-common` 零第三方框架依赖，仅 Lombok + Slf4j

**Admin 端（`zestflow-admin`）：**
- `AdminApplication` 加 `@EnableScheduling` 启用 OfflineMonitor
- `SecurityConfig` 放行 `POST/DELETE /registry/**`（无需 JWT）
- `ErrorCode` 新增 `EXECUTOR_NOT_FOUND`、`EXECUTOR_OFFLINE`
- `RegistryController` — `POST /registry/register`、`POST /registry/heartbeat`、`DELETE /registry/{executorId}`
- `RegistryServiceImpl` — 注册（upsert 模式，moduleCode 不存在则自动创建模块）、心跳（更新 lastHeartbeat）、注销
- 模块自动创建（Nacos 风格线程安全）：利用 `uk_code` 唯一约束 + `DuplicateKeyException` 兜底并发冲突
- `OfflineMonitor` — `@Scheduled` 每 30s 扫描 `status=1 && lastHeartbeat < now-90s` 的执行器标记为 `ABNORMAL(2)`
- 三态模型：`ONLINE=1`（在线）、`OFFLINE=0`（主动下线）、`ABNORMAL=2`（异常离线）
- `ExecutorRegistryServiceImpl.updateStatus()` — 手动上下线 API（`PUT /modules/executors/{id}/status`）
- DDL：`executor_registry` 表无 `retry_count` 字段，`module_id` 可空，`app_name` 做分组标识

**Executor 端（`zestflow-executor`）：**
- Spring Boot AutoConfiguration（`ExecutorAutoConfig`），业务项目引入自动生效
- `ExecutorProperties` — `zestflow.executor.*` 配置前缀（moduleCode, moduleName, adminAddresses, host, port）
  - `moduleCode`：未配置时自动取 `spring.application.name` → `"default"`（可覆盖）
  - `adminAddresses`：默认 `http://localhost:8080`，逗号分隔支持多地址高可用
  - `accessToken`：可选，Admin 身份校验
  - `heartbeatInterval`：默认 `30`（秒）
  - `host`：未配置时自动探测内网 IPv4（遍历 `NetworkInterface` 跳过回环）（可覆盖）
  - `port`：默认 `9999`（Netty 服务端端口，非业务 Tomcat 端口）
  - `timeoutMs`：默认 `5000`（毫秒）
- `AdminClient` — HTTP 客户端，xxl-job 风格 first-success 注册策略，支持多 Admin 地址逗号分隔
- `ExecutorRegistrar` — `ApplicationRunner` 启动注册 + 指数退避重试 + `@PreDestroy` 主动注销
  - **重试策略**：注册失败按 1s→2s→4s→8s→16s→30s（上限）指数退避，无限重试
  - **日志渐变**：前 5 次 WARN，之后每 10 次 ERROR（防日志洪刷）
  - **心跳失败**：`registered=false` → 下次 tick 恢复重试注册，网络恢复后自动连上
- `ExecutorServer` — 基于 Netty 的嵌入式 HTTP 服务（独立于业务 Tomcat），接收 Admin 回调
  - 提供 `/health`（健康检查）和 `/execute`（链路执行入口，TODO）端点
  - IdleStateHandler 超时自动关闭空闲连接
  - `@Bean(initMethod = "start", destroyMethod = "stop")` 生命周期管理

**测试模块（`zestflow-executor-test`）：**
- 独立 Spring Boot 应用，模拟业务方引入 executor
- 端口 8081，自动注册到 Admin（`spring.application.name=test-executor` 作为 moduleCode）
- `application.yml` 极简配置：仅 `admin-addresses` + `heartbeat-interval`

**变更文件：**
- 新增：`zestflow-common/.../model/dto/RegisterDTO.java`（含 moduleCode + moduleName）
- 新增：`zestflow-common/.../model/dto/HeartbeatDTO.java`
- 新增：`zestflow-common/.../constant/RegistryConstants.java`（含三态常量）
- 新增：`zestflow-admin/.../controller/RegistryController.java`
- 新增：`zestflow-admin/.../service/RegistryService.java`
- 新增：`zestflow-admin/.../service/impl/RegistryServiceImpl.java`（含模块自动创建）
- 新增：`zestflow-admin/.../config/OfflineMonitor.java`
- 新增：`zestflow-executor/**`（执行器全部文件）
  - `registry/AdminClient.java`、`ExecutorAutoConfig.java`、`ExecutorProperties.java`、`ExecutorRegistrar.java`
  - `server/ExecutorServer.java`（Netty）、`ServerHandler.java`
  - `META-INF/spring/...AutoConfiguration.imports`
- 新增：`zestflow-executor-test/**`（测试项目）
- 修改：`init.sql`（三态注释，去 `retry_count`）、`AdminApplication`（@EnableScheduling）、`SecurityConfig`（放行 registry）
- 修改：`ExecutorRegistryPO/VO`（去 `retryCount`）、`ModulePO/VO/DTO`（去 `retryCount/retryInterval`）
- 修改：各 Service 层、前端类型定义和 UI
- 修复：`zestflow-admin/pom.xml` spring-boot-maven-plugin 加 `<execution>` 确保 repackage 触发

### 采集器模块 — EventCollector SPI + 三级异步流水线（2026-05）

**需求：** Executor 发射链执行事件，Collector 异步采集落地，Admin 通过 HTTP 只读查询。对标 Sentinel 的异步链路 + xxl-job 的日志采集模型。

**核心原则：** 日志采集绝不能影响正常业务（最高优先级）。

**Event 模型（`zestflow-common`）：**
- `common.model.dto.ChainEvent` — 事件数据单元，15 个字段
- 7 种事件类型枚举：`CHAIN_STARTED / NODE_STARTED / NODE_COMPLETED / NODE_FAILED / CHAIN_COMPLETED / CHAIN_FAILED / CHAIN_TIMEOUT`
- UUID 作为 eventId（分布式无中心依赖）
- `zestflow-common` 零第三方框架依赖，仅 Lombok + Slf4j

**SPI 层（`collector-core`）：**
- `collector.spi.EventCollector` — 采集器接口：`collect()` + `collectBatch()`，实现方保证幂等
- `collector.spi.EventQueryService` — 只读查询接口：`queryEvents()` / `countEvents()` / `getById()` / `queryStats()`
- `collector.model.dto.EventQuery` / `EventStats` / `EventStatsQuery` — 查询 DTO

**JDBC 实现（`collector-jdbc`，默认实现）：**
- 三级异步流水线：`AsyncEventPublisher`（executor 端）→ `JdbcEventCollector` → `ChainEventMapper` 批量 INSERT IGNORE
- 幂等保障：`uk_event_id` 唯一约束 + `INSERT IGNORE`
- REST 控制器（只读）：`POST /collector/events/query`、`GET /collector/events/{eventId}`、`POST /collector/events/stats`
- Token 认证：`X-Collector-Token` 请求头，未配置时不校验
- 条件装配：`@ConditionalOnClass` 确保无 web 环境不加载 Controller
- MyBatis-Plus 分页 + 自动填充

**Kafka 实现（`collector-kafka`）：**
- `KafkaEventCollector` — 通过 `KafkaTemplate<String,String>` JSON 序列化发送到指定 Topic
- 配置：`zestflow.collector.kafka.topic`（默认 `zestflow-events`）
- `@ConditionalOnProperty` — 配置 topic 后才创建 Bean

**RabbitMQ 实现（`collector-rabbitmq`）：**
- `RabbitEventCollector` — 通过 `RabbitTemplate` JSON 序列化发送到指定 Exchange
- 配置：`zestflow.collector.rabbitmq.exchange`（默认 `zestflow.events`）、`routingKey`（默认 `zestflow.event.#`）
- 自动声明 `TopicExchange` Bean

**Executor 端事件发布（`zestflow-executor`）：**
- `executor.event.EventPublisher` — 发布接口，约定 `publish()` 在 ≤1ms 内返回
- `executor.event.AsyncEventPublisher` — 异步实现：
  - **三级流水线**：有界内存队列 → 批量 drain 线程 → EventCollector.collectBatch()
  - **有界队列**：`LinkedBlockingQueue` 默认容量 8192，`offer()` 超时 ≤1ms
  - **批量代理**：独立 drain 线程，200 条 / 500ms 阈值批量提交
  - **熔断器**：连续 10 次失败 → 开启熔断 30s 冷却，冷却后自动半开重试
  - **磁盘降级**（可选）：队列满或熔断开启时写本地文件 `./collector-fallback/events-{yyyyMMdd}.log`
  - **优雅关闭**：`destroy()` 等待 drain 线程消费完毕，超时 5s
  - **条件装配**：`@ConditionalOnBean(EventCollector.class)` — 无 Collector 时不创建
- `ExecutorServer` / `ServerHandler` — 注入 `EventPublisher`，`/execute` 入口发射 `CHAIN_STARTED` 事件
- 配置前缀 `zestflow.executor.event.*`：`queue-capacity`、`batch-size`、`batch-max-wait-ms`、`circuit-breaker-threshold`、`circuit-breaker-cooldown-ms`、`disk-fallback-enabled`、`disk-fallback-dir`

**Admin 日志查询（`zestflow-admin` + `zestflow-admin-ui`）：**
- `admin.client.CollectorClient` — HTTP 防腐层客户端，封装与 Collector 的通信
- `admin.client.CollectorConfig` — 配置 `zestflow.collector.api-url` / `access-token`
- `admin.service.LogService` / `impl.LogServiceImpl` — 委托 CollectorClient 查询
- `admin.controller.LogController` — `POST /api/logs/events/query` 为前端提供接口
- 前端 `LogsPage.vue` — 筛选栏（事件类型多选 / 状态 / 时间范围 / 关键字）+ 表格 + 分页 + 详情弹窗
- i18n 中英文完整覆盖

**部署模式：**
- **嵌入式**（通过 `zestflow-starter`）：executor + collector-jdbc 集成在业务应用内，共享 web 容器
- **独立 Collector 服务**：单独部署 collector-jdbc（或 kafka/rabbitmq），业务应用仅引入 executor

**chain_event 表 DDL：**
```sql
CREATE TABLE IF NOT EXISTS `chain_event` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '自增主键',
    `event_id`      VARCHAR(64)  NOT NULL                 COMMENT '事件全局唯一 ID（UUID）',
    `event_type`    VARCHAR(32)  NOT NULL                 COMMENT '事件类型',
    `chain_id`      VARCHAR(64)  DEFAULT NULL             COMMENT '链实例 ID',
    `chain_name`    VARCHAR(128) DEFAULT NULL             COMMENT '链名称',
    `node_id`       VARCHAR(64)  DEFAULT NULL             COMMENT '节点实例 ID',
    `node_name`     VARCHAR(128) DEFAULT NULL             COMMENT '节点名称',
    `executor_id`   VARCHAR(128) DEFAULT NULL             COMMENT '执行器 ID',
    `app_name`      VARCHAR(64)  DEFAULT NULL             COMMENT '应用名',
    `params`        TEXT         DEFAULT NULL             COMMENT '执行入参 JSON',
    `result`        TEXT         DEFAULT NULL             COMMENT '执行结果 JSON',
    `error_message` TEXT         DEFAULT NULL             COMMENT '错误消息',
    `cost_ms`       BIGINT       DEFAULT NULL             COMMENT '执行耗时（毫秒）',
    `status`        TINYINT      DEFAULT NULL             COMMENT '节点状态：0-失败 1-成功',
    `timestamp`     BIGINT       NOT NULL                 COMMENT '事件发生时间戳（毫秒）',
    `metadata`      TEXT         DEFAULT NULL             COMMENT '扩展元数据 JSON',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_chain_id` (`chain_id`),
    KEY `idx_executor_id` (`executor_id`),
    KEY `idx_timestamp` (`timestamp`),
    KEY `idx_app_event` (`app_name`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链执行事件表';
```

**新增文件清单：**
- `zestflow-common/.../model/dto/ChainEvent.java`
- `collector-core/**`（SPI 接口 + 查询 DTO 共 5 文件）
- `collector-jdbc/**`（采集器 + 查询服务 + Controller + 配置 + Mapper + PO 共 10 文件）
- `collector-kafka/**`（KafkaEventCollector + AutoConfig 共 3 文件）
- `collector-rabbitmq/**`（RabbitEventCollector + AutoConfig 共 3 文件）
- `zestflow-executor/.../event/**`（EventPublisher + AsyncEventPublisher 共 2 文件）
- `zestflow-admin/.../client/**`（CollectorClient + Config + DTO 共 4 文件）
- `zestflow-admin/.../controller/LogController.java`
- `zestflow-admin/.../service/LogService.java` + `impl/LogServiceImpl.java`
- `zestflow-admin-ui/.../api/logs.ts`
- `zestflow-admin-ui/.../views/logs/LogsPage.vue`

### 清理记录

| 时间 | 内容 |
|------|------|
| 2026-05 | 移除 `module` 表 `retry_count`、`retry_interval` 字段（无实际用途） |
| 2026-05 | 移除 `executor_registry` 表 `retry_count` 字段（重试改为客户端指数退避，服务端无需记录） |

## 工作原则

1. 给出方案前先讲清楚**为什么**、**对标了哪个项目**
2. 编码优先参照 LiteFlow 和 xxl-job 的成熟设计模式
3. 核心编排引擎不依赖 Spring Boot，可独立使用（zestflow-common 零框架依赖）
4. 架构上预留 SPI 扩展点，先收敛再扩展（V1.0 只做必要模块）
5. **改动已有代码前先读文件**，不要基于猜测直接改
6. **确认修改范围** — 改动前先和用户确认影响范围，不碰用户没要求的代码，特别是已有稳定功能的页面。**回滚时要精确确认哪些是本次改的、哪些是之前遗留的系统级防护**，不可连带回滚。
7. **优先考虑防腐层**，外部依赖不能侵入业务代码
8. **推送前必须先问用户确认**，不得擅自 `git push`
