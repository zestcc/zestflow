# ZestFlow 反模式与禁止项

## 绝对禁止

| 禁止项 | 原因 |
|--------|------|
| 编造 `componentId` | ChainValidator 会拒绝；运行时不可执行 |
| 跳过 `validate_chain` | AI 输出不可信 |
| 自动 `publish` / `reload` | Copilot ≠ Autopilot |
| 引用未部署的 Java 类 | 元件须 Executor 扫描注册 |
| 浏览器/Admin 读开发者本机源码 | 架构不可行 |

## 常见反模式

- 在链里硬编码业务 id，应走 ctx 传参  
- 复制 Admin 脚手架 Java 后不改包名  
- 同一 `componentId` 多方法重复注册  
- 用 Groovy/脚本绕过 `@ZestComponent` 白名单  

## 不确定时

列出需人工确认项，而不是猜测 id 或链编码。
