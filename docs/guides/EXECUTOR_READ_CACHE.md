# Executor 离线只读快照

Admin 代理 Executor 的 GET 列表（链 / 设计 / 元件）在 **Executor 离线或不可达** 时，返回最近一次成功拉取的 JSON 快照，并注入 `_readCache.stale=true`，前端展示只读提示。

## 架构

```
ExecutorProxyService.getFromExecutor()
  ├─ 在线 → RestTemplate GET → storeReadCache()
  └─ 离线/异常 → ExecutorReadCache.get() → attachReadCacheMeta()
```

| 实现 | 条件 | 说明 |
|------|------|------|
| `CaffeineExecutorReadCache` | `deploy-mode=standalone`（默认） | 单机内存，支持 `max-entries` |
| `RedisExecutorReadCache` | `deploy-mode=cluster` + Redis | 多 Admin 副本共享快照 |
| `NoopExecutorReadCache` | `enabled=false` | 关闭快照，离线返回空列表 |

写操作（POST/PUT/DELETE 代理）成功后按 `appCode` 调用 `invalidateApp()` 失效快照。

## 配置

前缀 `zestflow.admin.executor-read-cache.*`（见 [CONFIGURATION.md](../reference/CONFIGURATION.md)）

```yaml
zestflow:
  admin:
    deploy-mode: cluster   # cluster 时自动使用 Redis 实现
    executor-read-cache:
      enabled: true
      ttl-minutes: 60
      max-entries: 500     # 仅 Caffeine 生效
```

集群需配置 `spring.data.redis.*`（与 SSO PKCE、注册表存活表共用连接）。

## 前端

| 页面 | 说明 |
|------|------|
| 链列表 `/chains` | `ExecutorReadCacheAlert` + `consumeExecutorReadCacheMeta` |
| 设计列表 `/design` | 同上 |
| 元件列表 `/components` | 同上 |
| 调度中心 `/schedules` | 创建调度时链下拉走快照（accumulate 模式） |

组件：`src/components/ExecutorReadCacheAlert.vue`  
Composable：`src/composables/useExecutorReadCache.ts`

## 验证

1. 启动 Admin，确保 demo-app Executor 在线，打开链列表（写入快照）
2. 停止 demo 应用或标记 Executor 离线
3. 刷新链/设计/元件列表 → 仍能看到历史数据 + 顶部黄色只读提示
4. 恢复 Executor 在线 → 提示消失，数据刷新
5. cluster 模式：两 Admin 实例共享 Redis 快照（任一实例写入，另一实例可读）

## API 响应示例

```json
{
  "records": [{ "code": "CHN001", "name": "demo" }],
  "total": 1,
  "current": 1,
  "size": 10,
  "_readCache": {
    "stale": true,
    "cachedAt": 1718280000000
  }
}
```

前端解析后会移除 `_readCache` 字段，避免污染表格数据。
