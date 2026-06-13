# 日志执行轨迹实时流（SSE）

Admin 日志详情抽屉在链**执行进行中**时，通过 SSE 轮询 Collector 聚合结果，自动刷新执行图节点着色（轻量替代 WebSocket）。

## 端点

```
GET /api/zestflow/logs/executions/{executionId}/stream?appCode={appCode}
Accept: text/event-stream
Authorization: Bearer <JWT>
```

## 事件

| event | 说明 |
|-------|------|
| `connected` | 连接建立 |
| `waiting` | Collector 尚无该 executionId 数据 |
| `trace` | 轨迹 JSON（与 GET `/logs/executions/{id}` 同结构） |
| `done` | 链已到终态（成功/失败/超时） |
| `error` | 流异常 |

轮询间隔 2s，最长 10 分钟（SseEmitter 超时）。

## 前端

- `src/api/logsStream.ts` — fetch + SSE 解析
- `LogsPage.vue` — 详情抽屉打开且非终态时订阅；关闭抽屉自动 abort
- 执行列表 Tab 每 15s 自动刷新

## 验证

1. 试验场或 API 触发长耗时链
2. 打开日志 → 执行列表 → 点击进行中记录
3. 抽屉顶部出现「实时刷新中」，节点随执行逐步变绿/变红
4. 链结束后标签消失，列表自动刷新

## 与 WebSocket 的关系

当前为 **SSE + 服务端轮询 Collector**，零侵入 Executor/Collector 协议，适合 v0.2。后续若需亚秒级推送，可在 Collector 侧增加事件总线再升级 WebSocket。
