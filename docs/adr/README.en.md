# Architecture Decision Records (ADR) Index

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](README.md) · [← Documentation hub](../README.en.md)

Architecture Decision Records capture significant design choices for ZestFlow. Each ADR explains context, the decision taken, and consequences. They complement the full architecture document [ARCHITECTURE.en.md](../ARCHITECTURE.en.md).

---

## Documents

### [SCHEDULING.md](SCHEDULING.en.md)

**Status**: Accepted · **Version**: 0.2

Defines ZestFlow's **scheduling architecture** under the Hub control plane + business data plane separation model. Key decisions: Admin no longer scans business Cron (removed `admin.schedule.scan`); chain schedules live in the business DB (`zf_schedule` / `zf_schedule_log`); Executor runs **EmbeddedScheduleDriver** by default for local Cron, sharding, and in-process chain execution; SLA scanning moves to Collector; registry offline detection becomes event-driven. Admin retains platform-only Cron (tenant cleanup, etc.) and HTTP proxy for schedule CRUD.

### [SCHEDULING_SPI_XXLJOB.md](SCHEDULING_SPI_XXLJOB.en.md)

**Status**: Accepted · **Version**: 0.1.0

Documents **external scheduling via xxl-job** as an alternative to embedded scheduling. Explains `ScheduleDriver` SPI switching (`embedded` | `noop` | `external`), YAML configuration for xxl-job admin addresses, the built-in `zestflowChainJob` handler that delegates to `ChainExecuteFacade.executeCore`, idempotency keys, and HTTP `/execute` fallback when xxl-job is not used. Hub schedule UI behavior differs between embedded (read/write business DB) and external (Cron configured in xxl-job Admin) modes.

---

## Related documentation

| Topic | Document |
|-------|----------|
| Full scheduling section in architecture | [ARCHITECTURE.en.md §5.5.4](../ARCHITECTURE.en.md) |
| Scheduling acceptance tests | [acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.md](../acceptance/SCHEDULING_SLA_REGISTRY_ACCEPTANCE.en.md) |
| Deployment | [DEPLOY.en.md](../DEPLOY.en.md) |
