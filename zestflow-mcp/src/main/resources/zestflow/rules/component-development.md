# ZestFlow 元件开发规范

## 注解体系

| 注解 | 用途 |
|------|------|
| `@ZestComponent("group")` | 类级分组，Spring `@Component` 同存 |
| `@ZestExecute("componentId")` | 执行型元件（最常用） |
| `@ZestPredicate` | 条件分支 |
| `@ZestSelector` | 多路选择 |
| `@ZestLoader` / `@ZestParser` | 数据加载与解析 |
| `@ZestParam("key")` | 方法参数映射链上下文 |

## 命名约定

- `componentId`：全局唯一，camelCase，如 `deductStock`
- 类名：`XxxComponent` 或业务语义 + `Handler`
- 包路径：与业务工程一致，推荐 `...component` 或 `...handler`

## 代码模板

```java
@Slf4j
@Component
@ZestComponent("order")
public class StockComponent {

    @ZestExecute(value = "deductStock", name = "扣减库存")
    public Object deductStock(ChainContext ctx) {
        // 从 ctx 读取参数，执行业务，写回 ctx
        return result;
    }
}
```

## 门禁

1. **生成前** 调用 `list_components`，确认 id 不冲突  
2. **编译** `mvn compile`（本地）  
3. **注册** 部署后 Admin 元件列表可见  
4. **禁止** MCP/AI 自动 publish 或 reload
