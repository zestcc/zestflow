# 从 0.x 迁移到 1.0.0

> **类型**：Reference · [← 文档中心](README.md) · [English](MIGRATION_0.x_to_1.0.en.md)（待同步）

## 版本对照

| 0.x | 1.0.0 |
|-----|--------|
| Maven `0.1.0` / `0.2.0` | `1.0.0` |
| Admin 默认端口 8080 | 不变；本地可用 `application-local.yml` 改端口 |
| SSE 日志流 | SSE **与** WebSocket 双通道（WS 默认开启） |

## 依赖升级

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 配置变更

### 新增 / 默认值变化

```yaml
zestflow:
  admin:
    log-live-stream:
      # 1.0 默认 true；生产可关
      websocket-enabled: true
```

### 节点降级 config（可选）

链节点 `config` 支持：

| key | 值 | 行为 |
|-----|-----|------|
| `fallbackMode` | `default` | 记录日志，返回 null（与 0.x 一致） |
| `fallbackMode` | `constant` | 需配合 `fallbackConstant`，写入上下文 |
| `fallbackMode` | `propagate` | 将原异常重新抛出，降级失败 |

仍优先使用 `fallbackComponent` 指定降级元件。

## 数据库

- Admin 库：继续使用 Flyway `db/migration/V*.sql`，从 0.x 升级只需**重启 Admin**，无需删库。
- Executor/Collector 业务库：仍用 `init.sql` + 增量迁移脚本；无 1.0 破坏性 DDL。

## API 稳定性（1.0 冻结）

以下协议在 1.x 内保持向后兼容，Breaking 仅进 2.0：

- Netty `POST /execute` 响应体（`ChainExecuteResultDTO`）
- `POST /registry/register`、`/registry/heartbeat`
- Collector `chain_event` 事件字段
- Admin JWT 登录与 `Authorization: Bearer`

## 验收建议

升级后运行：

```powershell
.\scripts\blackbox\run-v1-acceptance.ps1 -SkipMavenTest
```

（首次建议全量含 `mvn test`）
