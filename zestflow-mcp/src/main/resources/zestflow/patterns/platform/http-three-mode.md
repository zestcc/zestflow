# HTTP 三 Mode（平台 L1）

| Mode | HTTP 入口 | Controller | 响应 |
|------|-----------|------------|------|
| **1** | `POST /api/execute` | 不需要 | 链末 **PARSER** 返回值 |
| **2** | 链 `config.http.path` | 不需要 | 同上；需 `chain-route-enabled=true` |
| **3** | 自定义 `@RestController` | **需要** | `ChainGateway.executeOrThrow`；失败抛异常便于事务 |

## Mode 1 请求体

```json
{ "chainCode": "CHN_XXX", "params": { "phone": "138..." } }
```

## Mode 2 链配置

```json
"config": {
  "http": {
    "path": "/api/users/register",
    "method": "POST",
    "produces": "application/json"
  }
}
```

## 门禁

- Mode 1/2 **必须**有 PARSER 终节点
- 禁止编造 componentId
- validate_chain 通过后才可 Playground/发布
