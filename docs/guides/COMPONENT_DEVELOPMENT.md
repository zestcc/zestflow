# 元件开发指南

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** How-to Guide

本指南说明如何在 Spring Boot 业务项目中编写 ZestFlow 元件（Component），并在 Admin 中被扫描、编排与执行。

---

## 1. 引入依赖

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

`zestflow-starter` 聚合 `zestflow-executor` + `collector-jdbc`，引入后自动装配执行引擎与 JDBC 事件采集。

---

## 2. 核心注解

| 注解 | 作用域 | 说明 |
|------|--------|------|
| `@ZestComponent("code")` | 类 | 声明元件组，`code` 为元件编码前缀 |
| `@ZestExecute(value, name)` | 方法 | 可编排的执行节点 |
| `@ZestParam` | 参数 | 从链上下文注入参数 |
| `@ZestPredicate` | 方法 | 条件判断（供 CONDITION 节点引用） |
| `@ZestAggregator` | 方法 | 结果聚合 |
| `@ZestParamValidator` | 方法 | 入参校验 |

完整速查见 [QUICK_REFERENCE.md](../QUICK_REFERENCE.md)。

---

## 3. 基础示例

```java
@Component
@ZestComponent("order")
public class OrderHandler {

    @ZestExecute(value = "createOrder", name = "创建订单")
    public OrderResult createOrder(
            @ZestParam(value = "userId", required = true) String userId,
            @ZestParam(value = "amount", required = true) Double amount) {
        // 业务逻辑
        return new OrderResult("ORD-" + System.currentTimeMillis(), amount);
    }

    @ZestPredicate("isVip")
    public boolean isVip(@ZestParam("userId") String userId) {
        return userId.startsWith("VIP");
    }
}
```

**约定：**

- 元件类须为 Spring `@Component`（或由 `@Configuration` 注册为 Bean）
- `@ZestExecute.value` 全局唯一，推荐 `{componentCode}.{methodValue}` 形式
- 返回值可序列化为 JSON，写入链上下文供下游节点使用

---

## 4. 参数绑定

链执行时，Admin / 设计器传入的 JSON 参数映射到 `@ZestParam`：

```java
@ZestExecute(value = "ship", name = "发货")
public void ship(
        @ZestParam("orderId") String orderId,
        @ZestParam(value = "express", required = false, defaultValue = "SF") String express) {
    // ...
}
```

上下文键名默认与 `value` 一致；上游节点输出可通过参数绑定器（设计器配置）映射到当前节点。

---

## 5. 链声明（可选）

使用 `@ZestChain` 在代码中声明链占位，启动时同步到业务库：

```java
@ZestChain(key = "orderFlow", name = "订单流程")
public class OrderChainDeclaration {}
```

配合 `zestflow.executor.chain.declaration-sync-enabled=true`（默认开启），Admin 可见链骨架，再在设计器中补充 DAG。

---

## 6. 配置要点

```yaml
spring:
  application:
    name: my-shop          # 默认 moduleCode / appCode

zestflow:
  executor:
    admin-addresses: http://localhost:8080
    port: 20550            # Netty 回调端口
    access-token:          # 生产环境必填，与 Admin 一致
    registry-token:        # 注册/心跳令牌
    chain:
      auto-reload: true    # 链定义热更新
```

完整配置见 [reference/CONFIGURATION.md](../reference/CONFIGURATION.md)。

---

## 7. 验证元件

1. 启动业务应用，确认日志 `register success`
2. Admin → **元件管理**：应列出扫描到的 `@ZestExecute` 方法
3. **设计器** 拖入「执行器」节点，绑定元件与方法
4. **试验场** 或 `POST /execute`（Netty 20550）试跑

---

## 8. 最佳实践

| 实践 | 原因 |
|------|------|
| 单方法单职责 | 便于观测、重试与热替换 |
| 避免在元件内写长 if-else 流程 | 流程应交链编排表达 |
| 返回明确 DTO 而非裸 Map | 便于日志序列化与 Copilot 理解 |
| 敏感字段脱敏后再返回 | 事件采集会记录 result |
| 幂等设计 | 节点可能重试（`ERROR_STRATEGY_RETRY`） |

---

## 9. Dev MCP 辅助开发

IDE 中可接入 `zestflow-mcp`，获得元件模板、规范检查与链意图提示。见 [MCP_SETUP.md](../MCP_SETUP.md) 与 [AI_IDE_SETUP.md](../AI_IDE_SETUP.md)（Cursor / Claude / VS Code / Windsurf 全场景）。

---

## 相关文档

- [CHAIN_ORCHESTRATION.md](CHAIN_ORCHESTRATION.md) — 在设计器中编排元件
- [ARCHITECTURE.md](../ARCHITECTURE.md) §5.2 — Executor 引擎 internals
- [QUICK_REFERENCE.md](../QUICK_REFERENCE.md) — API 速查
