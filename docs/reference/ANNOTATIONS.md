# 注解参考

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** Reference · [← 文档中心](../README.md) · [English](ANNOTATIONS.en.md)
> **源码包：** `com.zestflow.executor.annotation`

---

## 1. 核心编排注解

### @ZestComponent

标记 Spring Bean 为 ZestFlow 元件容器。Meta-annotated `@Component`。

| 属性 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `value` | String | `""` | 元件组编码，如 `"order"` |

```java
@Component  // 或由 @ZestComponent 隐式注册
@ZestComponent("order")
public class OrderHandler { }
```

### @ZestExecute

标记可编排的执行方法。对应设计器「执行器」节点绑定的元件。

| 属性 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `value` | String | `""` | 元件 ID；**空则取方法名** |
| `name` | String | `""` | Admin 展示名称 |
| `description` | String | `""` | 描述 |
| `timeout` | long | `-1` | 毫秒；`-1`=用节点配置，`0`=无限 |
| `async` | boolean | `false` | 异步执行（不等待结果） |

**全局唯一 ID 规则：** 推荐 `{componentCode}.{methodValue}`，如 `order.createOrder`。

```java
@ZestExecute(value = "createOrder", name = "创建订单", timeout = 30000)
public OrderCreatedResult createOrder(
        @ZestParam("userId") String userId,
        @ZestParam("amount") double amount) {
    return new OrderCreatedResult("ORD-001", amount);
}
```

### @ZestChain

在代码中声明链占位，启动时同步到业务库（需 `declaration-sync-enabled=true`）。

| 属性 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `value` | String | **必填** | chain_key |
| `name` | String | `""` | 展示名 |
| `description` | String | `""` | 描述 |

**Target：** `TYPE`, `METHOD`

### @ZestParam

从链上下文（DataBus）注入参数。

| 属性 | 类型 | 默认 | 说明 |
|------|------|------|------|
| `value` | String | `""` | DataBus key；空=参数名 |
| `required` | boolean | `false` | 是否必填 |
| `defaultValue` | String | `""` | 默认值（字符串解析） |
| `source` | String | `"databus"` | `databus` / `header` / `request` |
| `converter` | String | `""` | 自定义 `ParamConverter` Bean 名 |

---

## 2. 流程控制注解

| 注解 | 用途 | 关键属性 |
|------|------|---------|
| `@ZestPredicate` | 条件分支（CONDITION 节点） | `value`, `name` |
| `@ZestSelector` | 多路选择（SELECTOR 节点） | 配合 `@ZestTag` |
| `@ZestParser` | 链终态解析（PARSER） | — |
| `@ZestLoader` | 数据加载 | — |
| `@ZestTransformer` | 数据转换 | — |
| `@ZestSplitter` | 数据拆分 | — |
| `@ZestFilter` | 数据过滤 | — |
| `@ZestAggregator` | 数据聚合 | `strategy` 默认 `"ALL"` |
| `@ZestErrorHandler` | 链/节点错误处理 | — |

### @ZestTag / @ZestTags

用于 `@ZestPredicate` / `@ZestSelector` 的分支标签映射。

```java
@ZestPredicate(value = "auditAfterSale", name = "售后审核")
@ZestTag(name = "同意", value = "true")
@ZestTag(name = "不同意", value = "false")
public boolean auditAfterSale(@ZestParam("applyId") String applyId) {
    return true;
}
```

---

## 3. 生命周期注解

| 注解 | 阶段 | 说明 |
|------|------|------|
| `@ZestPreProcessor` | 执行前 | 拦截器链 |
| `@ZestPostProcessor` | 执行后 | 拦截器链 |
| `@ZestParamBinder` | 参数绑定 | 自定义绑定逻辑 |
| `@ZestParamValidator` | 参数校验 | 校验失败抛异常 |

---

## 4. 集成型注解

| 注解 | 属性 | 默认 |
|------|------|------|
| `@ZestHttpClient` | `method`, `url` | `GET`, `""` |
| `@ZestMqProducer` | `topic` | `""` |
| `@ZestMqConsumer` | `topic` | `""` |
| `@ZestCacheReader` | `cacheKey` | `""` |
| `@ZestCacheWriter` | `ttlSeconds` | `3600` |
| `@ZestLogger` | `level` | `"INFO"` |
| `@ZestDelay` | `delayMs` | `1000` |

---

## 5. 输出与上下文

| 注解 | Target | 说明 |
|------|--------|------|
| `@ZestOutput("key")` | METHOD | 指定输出写入 DataBus 的 key |
| `@ZestResult` | PARAMETER | 标记型，注入上一节点结果 |
| `@ZestFailure` | PARAMETER | 标记型，注入失败上下文 |

也可直接注入 `ChainContext`：

```java
@ZestExecute("process")
public void process(ChainContext ctx) {
    ctx.put("key", value);
    String userId = ctx.get("userId", String.class);
}
```

---

## 6. 安全

### @Sensitive

日志/事件采集时对字段脱敏。

| 属性 | 默认 | 说明 |
|------|------|------|
| `strategy` | `MASK_ALL` | 脱敏策略 |
| `keepPrefix` / `keepSuffix` | `0` | 保留位数 |

**MaskStrategy 枚举：** `MASK_ALL`, `MASK_MIDDLE`, `PREFIX_ONLY`, `SUFFIX_ONLY`, `PHONE`, `EMAIL`, `ID_CARD`

---

## 7. AI 元件生成

### @AiComponentGenerate（`component.ai` 包）

| 属性 | 说明 |
|------|------|
| `description` | **必填**，元件描述 |
| `inputKeys` | 输入 DataBus key 数组 |
| `outputKey` | 输出 key |
| `category` | `BUSINESS` / `TRANSFORM` / `INTEGRATION` / `VALIDATION` / `FLOW_CONTROL` |

---

## 8. 注意事项

1. **元件类必须是 Spring Bean**（`@Component` 或 `@ZestComponent` 隐式注册）。
2. **`@ZestExecute` 方法应幂等**，节点可能 RETRY。
3. **返回值会进入事件采集**，敏感数据用 `@Sensitive` 或避免返回。
4. **不要用长 if-else 表达流程**，应交链 DAG 编排。
5. 扫描后调用 `POST /api/components/refresh` 可热更新元件列表。

---

## 相关文档

- [guides/COMPONENT_DEVELOPMENT.md](../guides/COMPONENT_DEVELOPMENT.md) — 开发指南
- [EXECUTION_ENGINE.md](EXECUTION_ENGINE.md) — 编程式执行
- [API.md](API.md) — REST 接口
