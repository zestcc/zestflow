# 外部调度接入（xxl-job / SPI external）

> **版本** 0.1.0 · **更新** 2026-06-08 · **类型** ADR · [← 文档中心](../README.md) · [English](SCHEDULING_SPI_XXLJOB.en.md)

ZestFlow 默认使用 **EmbeddedScheduleDriver**（Executor 读业务库 `zf_schedule` 本地 Cron）。

## 1. 切换驱动

```yaml
zestflow:
  executor:
    schedule:
      enabled: true
      driver: external   # embedded | noop | external
      xxl-job:
        enabled: true
        admin-addresses: http://127.0.0.1:8080/xxl-job-admin
        access-token: default_token
        appname: zestflow-executor
        port: 9999
        log-path: /data/applogs/xxl-job/jobhandler
```

`driver=noop`：关闭本地 Cron，仅 HTTP `/execute` 或外部触发。

## 2. 内置 Handler（已实现）

类：`com.zestflow.executor.schedule.external.XxlJobChainJobHandler`

| JobHandler | 参数 | 行为 |
|------------|------|------|
| `zestflowChainJob` | chainCode | `ChainExecuteFacade.executeCore`，source=xxl-job |

幂等键：`xxl-{jobId}-{epochMs}`

## 3. HTTP 触发（无 xxl-job 时）

```http
POST /execute
X-Access-Token: {token}
{"chainCode":"demo-chain","source":"xxl-job","idempotencyKey":"..."}
```

## 4. Hub 调度 UI

- **Embedded**：Admin 写业务库 `zf_schedule`
- **external**：Cron 在 xxl-job Admin 配置；ZestFlow 调度中心 CHAIN Tab 可只读

详见 [SCHEDULING.md](./SCHEDULING.md)。
