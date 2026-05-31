# ZestFlow Admin 单元测试报告

## 执行结果

| 指标 | 数值 |
|------|------|
| 测试总数 | 154 |
| 通过 | 154 |
| 失败 | 0 |
| 错误 | 0 |
| 跳过 | 0 |
| 成功率 | **100%** |

## 测试类明细

### 配置层

| 测试类 | 方法数 | 测试内容 |
|--------|--------|----------|
| `MyMetaObjectHandlerTest` | 6 | MetaObjectHandler 自动填充逻辑：insertFill/updateFill 在有/无认证上下文时的用户名回退、时间戳填充 |

### Controller 层

| 测试类 | 方法数 | 测试内容 |
|--------|--------|----------|
| `ChainControllerTest` | 12 | 链管理接口权限拦截：list/create/delete/update/publish/rollback/toggleStatus 各方法的 VIEWER/EDITOR/ADMIN 角色校验、未认证拦截、超管放行 |
| `ComponentControllerTest` | 8 | 元件接口权限：list 的 appCode 参数处理、空白/空值跳过、无权限拒绝、stats 权限校验 |
| `DesignControllerTest` | 15 | 设计编排接口权限：list/getByCode/create/update/saveGraph/delete/toggleStatus/bind/unbind 的角色校验、缺失 appCode 处理、未认证拦截 |

### Service 层

| 测试类 | 方法数 | 测试内容 |
|--------|--------|----------|
| `RegistryServiceImplTest` | 10 | 执行器注册/心跳/注销：新注册插入、已注册更新、状态维护、离线标记、自动创建字典项 |
| `ExecutorRegistryServiceImplTest` | 10 | 执行器管理：状态更新（成功/NotFound/Invalid）、listAll 超管/普通用户过滤、getByExecutorId、listDistinctApps |
| `CollectorRegistryServiceImplTest` | 17 | 采集器注册/心跳/注销：新注册、重注册、旧格式迁移、同地址去重、listAll 超管/普通用户过滤、listAllOnline、updateStatus |
| `DashboardServiceImplTest` | 5 | 仪表盘统计：superAdmin/normalUser 过滤、无在线执行器跳过代理、采集器故障优雅降级、代理异常传播 |
| `DictTypeServiceImplTest` | 23 | 字典类型管理：CRUD（含重复检测）、缓存、分页（关键字/状态过滤）、应用级权限隔离、ensureDictData 自动创建 |
| `ScheduleServiceImplTest` | 14 | 调度管理：CRUD、list 超管/普通用户过滤、trigger 成功/无执行器/路由失败、listLogs |
| `PermissionServiceImplTest` | 17 | 权限校验核心逻辑：超管检测、appCode 获取、角色层级（VIEWER<EDITOR<ADMIN）、多线程并发安全 |
| `TenantAppContextTest` | 13 | 租户上下文：tenantId 读取、appCode 获取（无认证/超管/普通用户/异常/跨线程隔离）、hasEditPermission |

### 调度层

| 测试类 | 方法数 | 测试内容 |
|--------|--------|----------|
| `RouteStrategyTest` | 4 | 路由策略：轮询、一致性哈希、随机选择 |

## 覆盖的功能场景

### 多租户 + app_code 数据隔离（核心）

- **Service 层查询过滤**：ExecutorRegistry/CollectorRegistry/Schedule/Dashboard 的 list/listAll/stats 方法在普通用户下按 appCode 过滤数据，超管不过滤
- **Controller 权限拦截**：所有业务端点在方法入口校验当前用户是否拥有指定 appCode 的 VIEWER/EDITOR/ADMIN 角色
- **角色层级**：APP_VIEWER(1) < APP_EDITOR(2) < APP_ADMIN(3)，高角色覆盖低角色所有权限
- **跨线程安全**：SecurityContext 不继承到子线程，子线程无认证上下文时返回空 appCode 集合

### 执行器/采集器注册

- 新注册插入、已注册更新（host/port）
- 旧格式 collectorId 迁移（按 host:port 匹配）
- 同地址重复记录合并（删除多余记录）
- 三态模型：ONLINE(1)/OFFLINE(0)/ABNORMAL(2)
- 心跳更新 lastHeartbeat

### 字典管理

- CRUD 完整覆盖（含重复 code 检测）
- 应用级字典 vs 系统级字典（appCode 空/非空）
- `ensureDictData` 并发安全（type 不存在时自动创建）
- `getDictData` 缓存

### 异常路径

- 404 类：类型/数据不存在
- 403 类：无权限访问
- 校验错误：无效 status 值
- 外部故障：Collector 不可用时仪表盘优雅降级
- 代理异常：Executor 调用异常传播到调用方

### 并发安全

- `PermissionServiceImpl`：20 线程并发 `getAccessibleAppCodes`，CountDownLatch 同步
- `TenantAppContext`：跨线程 SecurityContext 不继承验证
- 注册去重：`DuplicateKeyException` 兜底并发冲突

## 测试技术栈

| 技术 | 用途 |
|------|------|
| JUnit 5 (Jupiter) | 测试框架 |
| Mockito 5 (Strict stubs) | Mock、桩函数、参数捕获 |
| AssertJ | 流式断言（`assertThat`, `assertThatThrownBy`）|
| MockitoExtension | 自动清理 mock 状态 |
| MockitoSettings(LENIENT) | 部分测试放宽 stub 校验 |
| SystemMetaObject | MetaObjectHandler 测试中创建真实 MetaObject |
| CountDownLatch | 并发安全测试 |

## 测试编写日期

2026-05-31
