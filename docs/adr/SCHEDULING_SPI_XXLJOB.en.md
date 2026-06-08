# External Scheduling Integration (xxl-job / SPI external)

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](SCHEDULING_SPI_XXLJOB.md) · **Type** ADR · [← Documentation hub](../README.en.md)

ZestFlow default uses **EmbeddedScheduleDriver** (Executor reads business DB `zf_schedule` for local Cron).

## 1. Switch Driver

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

`driver=noop`: Disable local Cron; only HTTP `/execute` or external triggers.

## 2. Built-in Handler (Implemented)

Class: `com.zestflow.executor.schedule.external.XxlJobChainJobHandler`

| JobHandler | Parameter | Behavior |
|------------|-----------|----------|
| `zestflowChainJob` | chainCode | `ChainExecuteFacade.executeCore`, source=xxl-job |

Idempotency key: `xxl-{jobId}-{epochMs}`

## 3. HTTP Trigger (Without xxl-job)

```http
POST /execute
X-Access-Token: {token}
{"chainCode":"demo-chain","source":"xxl-job","idempotencyKey":"..."}
```

## 4. Hub Schedule UI

- **Embedded:** Admin writes business DB `zf_schedule`
- **external:** Cron configured in xxl-job Admin; ZestFlow schedule center CHAIN Tab may be read-only

See [SCHEDULING.en.md](./SCHEDULING.en.md).
