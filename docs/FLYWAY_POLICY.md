# ZestFlow Flyway 策略（Admin / Demo）

> **目标**：开发环境升级 jar **不因 Beta history 漂移挡启动**；生产环境 **严格顺序 + 校验**，保证可预期。  
> **脚本目录**：`zestflow-admin/src/main/resources/db/migration/`

---

## 1. 分层策略

| 环境 | Profile | validate-on-migrate | out-of-order | 启动行为 |
|------|---------|---------------------|--------------|----------|
| **本地 / dev** | `!prod` | `false` | `true` | legacy 检测 → `repair → migrate` |
| **公网试玩 demo** | `demo` 等 | `false` | `true` | 同上 |
| **生产** | `prod` | **`true`** | **`false`** | 默认 Spring migrate；`AdminProductionGuard` 强制校验配置 |

实现类：

- `NonProductionFlywayConfiguration` + `ZestFlowFlywayPolicies` + `FlywayLegacyHistoryCleaner`（admin）
- `ProductionFlywayConfiguration`（admin prod）
- `DemoFlywayPolicies`（executor demo 库，若启用）

---

## 2. Rebaseline（2026-06-08）

旧 Beta 链 `V1 → V2 beta → V4 → V5 → V6`（跳 V3）已 **squash** 为连续链：

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__init_admin_schema.sql` | 全量表结构基线 |
| V2 | `V2__platform_schedule_v02.sql` | 平台调度 v0.2（停用 Admin 侧任务 + Collector SLA） |
| V3 | `V3__ai_learning_event.sql` | AI Chain-first 学习事件表 |

**下一增量**：`V4__*.sql`（禁止跳号）。

---

## 3. 脚本编写铁律

1. **已发布版本的 `V{n}__*.sql` 禁止改内容** — 只能新增 `V{n+1}__*.sql`（改 checksum 会导致 prod validate 失败）。
2. **V2 起必须幂等** — DDL 用 `IF NOT EXISTS`；DML 用 `WHERE NOT EXISTS` / 条件 UPDATE。
3. **版本号唯一、连续、只增不减** — CI 门禁 `FlywayMigrationScriptsTest` 校验无跳号。
4. **Rebaseline 后 prod 库** — 若 history 含旧 V4/V5/V6，须 DBA 评审后手工对齐或重建，**不能**依赖非 prod 自动清 history。

---

## 4. 故障自愈（开发库）

| 现象 | 非 prod 行为 |
|------|----------------|
| history 含旧 V4/V5/V6 或旧 V2 beta | 启动时 `FlywayLegacyHistoryCleaner` 清空 history 并重放 V1→V3（脚本幂等） |
| `checksum mismatch` | 启动前 `repair()` |
| 表结构极老（缺 V1 列） | 删库重建或 `scripts/deploy/rebaseline-admin-dev.ps1` |

手工 SQL：`scripts/deploy/rebaseline-admin-dev.sql` 或 `scripts/deploy/templates/repair-flyway-admin.sql`。

**生产**出现 history 与 jar 不一致 → **禁止**自动清 history；走发布流程或 DBA 对齐。

---

## 5. 新环境 / 部署

```text
CREATE DATABASE zestflow_admin ...
配置 spring.datasource + spring.flyway.enabled=true
启动 Admin → Flyway migrate V1…V3
```

prod 配置见 `application-prod.example.yml`：

```yaml
spring.flyway:
  enabled: true
  validate-on-migrate: true
  out-of-order: false
```

---

## 6. CI 门禁

`FlywayMigrationScriptsTest` 校验：

- 迁移文件命名 `V{数字}__*.sql`
- 版本号无重复、**无跳号**（1…N 连续）

---

*详见 `db/migration/README.md` 快速索引。*
