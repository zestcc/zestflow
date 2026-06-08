# 快速入门

> **版本** 0.1.0 · **更新** 2026-06-08 · **预计耗时** 30 分钟 · [English](GETTING_STARTED.en.md)

本教程带你从零在本地运行 ZestFlow，并完成第一次链执行。完成后你将理解 Admin、Executor、Collector 三端协作关系。

---

## 前置条件

| 依赖 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.x |
| Node.js（仅改前端时） | 18 或 20 LTS |

---

## 1. 准备数据库

创建三个库（名称须与配置一致）：

```sql
CREATE DATABASE IF NOT EXISTS zestflow_admin DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS zestflow_app_bussiness DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS zestflow_app_log DEFAULT CHARSET utf8mb4;
```

**Admin 库**：启动 Admin 时 Flyway 自动执行 `db/migration/V*.sql`。

**业务库 / 日志库**：首次启动 Demo 时由 Flyway / init 脚本初始化（见 `zestflow-executor` 与 `collector-jdbc` 资源目录）。

---

## 2. 配置本地数据源

```bash
# Admin
cp zestflow-admin/src/main/resources/application-local.example.yml \
   zestflow-admin/src/main/resources/application-local.yml

# Demo（Executor + Collector）
cp zestflow-demo/src/main/resources/application-local.example.yml \
   zestflow-demo/src/main/resources/application-local.yml
```

编辑两个 `application-local.yml`，将 `your-db-username` / `your-db-password` 改为实际 MySQL 账号。

---

## 3. 编译并启动

```bash
# 安装依赖模块（首次或拉代码后）
mvn install -pl zestflow-demo -am -DskipTests

# 终端 1：Admin（端口 8080）
mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local

# 终端 2：Demo 业务应用（端口 8081，含 Executor + Collector）
mvn spring-boot:run -pl zestflow-demo -Dspring-boot.run.profiles=local
```

**预期结果：**

- Admin 日志出现 Flyway migrate 成功
- Demo 日志出现 `Executor register success` 与 Collector 注册信息
- 浏览器访问 http://localhost:8080 ，默认账号 `admin` / `admin123`（仅本地开发）
- （可选）OpenAPI 文档 http://localhost:8080/swagger-ui.html（local profile 开启 springdoc UI）

---

## 4. 验证三端连通

登录 Admin 后检查：

| 菜单位置 | 预期 |
|---------|------|
| **执行器管理** | 出现 `demo-app` 模块，状态在线 |
| **元件管理** | 扫描到 Demo 中 `@ZestComponent` 元件 |
| **采集器管理** | Collector 实例在线 |

端口对照（默认）：

| 组件 | 端口 | 说明 |
|------|------|------|
| Admin | 8080 | UI + REST API |
| Demo Tomcat | 8081 | 业务 HTTP |
| Executor Netty | 20550 | Admin 回调执行链 |
| Collector Netty | 20650 | Admin 查询事件 |

---

## 5. 执行第一条链（Playground）

1. 进入 **试验场 → 场景列表**
2. 选择任意内置场景（如售后处理相关场景）
3. 点击 **执行**，查看结构化响应与节点明细
4. 进入 **日志查询**，按链编码筛选，点击 trace 查看执行图

这验证了完整链路：**Admin 调度 → Executor Netty 执行 → Collector 落库 → Admin 查询展示**。

---

## 6. 编写你的第一个元件（可选）

在业务项目中添加依赖：

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
@ZestComponent("hello")
public class HelloHandler {

    @ZestExecute(value = "greet", name = "问候")
    public Map<String, Object> greet(@ZestParam("name") String name) {
        return Map.of("message", "Hello, " + name);
    }
}
```

配置最小项：

```yaml
spring.application.name: my-app
zestflow.executor.admin-addresses: http://localhost:8080
zestflow.executor.port: 20550
```

重启应用后，Admin **元件管理** 可见 `hello.greet`，在设计器中拖入画布即可建链。详见 [元件开发指南](guides/COMPONENT_DEVELOPMENT.md)。

---

## 7. 前端开发（可选）

```bash
cd zestflow-admin-ui
pnpm install
pnpm dev    # http://localhost:8001，API 代理到 8080
```

修改前端后须构建嵌入 Admin：

```bash
pnpm build   # 产物输出到 zestflow-admin/src/main/resources/static/
```

---

## 常见问题

**Demo 注册失败 / 执行器离线**

- 确认 Admin 已启动且 `zestflow.executor.admin-addresses` 指向 `http://localhost:8080`
- 检查防火墙是否放行 20550 / 20650（本地一般无问题）

**日志页无数据**

- 确认 Collector 在线；`zestflow.collector.api-url`（Admin 侧）默认 `http://localhost:20650`
- 执行 Playground 后再查日志

**Flyway 启动报错**

- 开发库可参阅 [FLYWAY_POLICY.md](FLYWAY_POLICY.md) 的 rebaseline 说明

---

## 下一步

| 目标 | 文档 |
|------|------|
| 深入理解架构 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| 编写复杂元件 | [guides/COMPONENT_DEVELOPMENT.md](guides/COMPONENT_DEVELOPMENT.md) |
| 可视化建链与发布 | [guides/CHAIN_ORCHESTRATION.md](guides/CHAIN_ORCHESTRATION.md) |
| 生产部署 | [DEPLOY.md](DEPLOY.md) |
| 配置项查阅 | [reference/CONFIGURATION.md](reference/CONFIGURATION.md) |
