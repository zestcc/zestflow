# ZestFlow Flyway Policy (Admin / Demo)

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](FLYWAY_POLICY.md) · **Type** How-to · [← Documentation hub](README.en.md)  
> **Goal**: Dev environments must not fail startup due to Beta history drift; production must enforce strict ordering and validation.  
> **Script directory**: `zestflow-admin/src/main/resources/db/migration/`

---

## 1. Layered strategy

| Environment | Profile | validate-on-migrate | out-of-order | Startup behavior |
|-------------|---------|---------------------|--------------|------------------|
| **Local / dev** | `!prod` | `false` | `true` | Legacy detection → `repair → migrate` |
| **Public demo** | `demo`, etc. | `false` | `true` | Same as above |
| **Production** | `prod` | **`true`** | **`false`** | Default Spring migrate; `AdminProductionGuard` enforces config |

Implementation classes:

- `NonProductionFlywayConfiguration` + `ZestFlowFlywayPolicies` + `FlywayLegacyHistoryCleaner` (admin)
- `ProductionFlywayConfiguration` (admin prod)
- `DemoFlywayPolicies` (executor demo DB, if enabled)

---

## 2. Rebaseline (2026-06-08)

The old Beta chain `V1 → V2 beta → V4 → V5 → V6` (skipped V3) has been **squashed** into a continuous chain:

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__init_admin_schema.sql` | Full schema baseline |
| V2 | `V2__platform_schedule_v02.sql` | Platform scheduling v0.2 (disable Admin-side tasks + Collector SLA) |
| V3 | `V3__ai_learning_event.sql` | AI Chain-first learning event table |

**Next increment**: `V4__*.sql` (no version gaps allowed).

---

## 3. Script authoring rules

1. **Never modify content of published `V{n}__*.sql`** — only add `V{n+1}__*.sql` (checksum changes cause prod validate failures).
2. **V2 onward must be idempotent** — DDL uses `IF NOT EXISTS`; DML uses `WHERE NOT EXISTS` / conditional UPDATE.
3. **Version numbers must be unique, continuous, and monotonically increasing** — CI gate `FlywayMigrationScriptsTest` checks for gaps.
4. **After rebaseline, prod DBs** with old V4/V5/V6 in history require DBA review for manual alignment or rebuild — **do not** rely on non-prod automatic history cleanup.

---

## 4. Self-healing (dev databases)

| Symptom | Non-prod behavior |
|---------|-------------------|
| History contains old V4/V5/V6 or old V2 beta | On startup, `FlywayLegacyHistoryCleaner` clears history and replays V1→V3 (scripts are idempotent) |
| `checksum mismatch` | `repair()` before migrate |
| Very old schema (missing V1 columns) | Drop and recreate DB, or run `scripts/deploy/rebaseline-admin-dev.ps1` |

Manual SQL: `scripts/deploy/rebaseline-admin-dev.sql` or `scripts/deploy/templates/repair-flyway-admin.sql`.

**Production** history mismatch with jar → **never** auto-clear history; follow release process or DBA alignment.

---

## 5. New environment / deployment

```text
CREATE DATABASE zestflow_admin ...
Configure spring.datasource + spring.flyway.enabled=true
Start Admin → Flyway migrate V1…V3
```

Prod config in `application-prod.example.yml`:

```yaml
spring.flyway:
  enabled: true
  validate-on-migrate: true
  out-of-order: false
```

---

## 6. CI gates

`FlywayMigrationScriptsTest` validates:

- Migration file naming `V{number}__*.sql`
- No duplicate version numbers, **no gaps** (continuous 1…N)

---

*See `db/migration/README.md` for a quick index.*
