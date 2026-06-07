# ZestFlow Flyway 策略（Admin / Demo）

> **目标**：开发环境升级 jar **不因 history 漂移挡启动**；生产环境 **严格顺序 + 校验**，保证可预期。  
> **脚本目录**：`zestflow-admin/src/main/resources/db/migration/`

---

## 1. 分层策略

| 环境 | Profile | validate-on-migrate | out-of-order | 启动行为 |
|------|---------|---------------------|--------------|----------|
| **本地 / dev** | `!prod` | `false` | `true` | `repair → migrate`，可补跑漏掉的 V2/V6 |
| **公网试玩 demo** | `demo` 等 | `false` | `true` | 同上 |
| **生产** | `prod` | **`true`** | **`false`** | 默认 Spring migrate；`AdminProductionGuard` 强制校验配置 |

实现类：

- `NonProductionFlywayConfiguration` + `ZestFlowFlywayPolicies`（admin）
- `ProductionFlywayConfiguration`（admin prod）
- `DemoFlywayConfig` + `DemoFlywayPolicies`（executor demo 库）

---

## 2. 脚本编写铁律（防「一改就挂」）

1. **已发布版本的 `V{n}__*.sql` 禁止改内容** — 只能新增 `V{n+1}__*.sql`（改 checksum 会导致 prod validate 失败）。
2. **V2 起必须幂等** — 列/表/索引用 `information_schema` 或 `IF NOT EXISTS`；数据变更用 `WHERE NOT EXISTS`。
3. **版本号唯一、只增不减** — 当前 Admin 链：`V1 → V2 → V4 → V5 → V6`（**V3 预留**）。
4. **禁止跳号发布** — 新脚本用「当前最大版本 + 1」（下一增量 **V7**）。

---

## 3. 版本清单（Admin）

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__init_admin_schema.sql` | 基线 |
| V2 | `V2__beta_schema_align.sql` | 存量库幂等对齐 |
| V4 | `V4__schedule_hub_embedded.sql` | 停用 admin.schedule.scan |
| V5 | `V5__registry_event_sla_collector.sql` | 注册/SLA 平台任务迁移 |
| V6 | `V6__ai_learning_event.sql` | AI 学习事件表 |

---

## 4. 故障自愈（开发库）

| 现象 | 非 prod 行为 |
|------|----------------|
| `Detected resolved migration not applied: 2` | 启动时 `out-of-order` 自动补跑 V2 |
| `checksum mismatch` | 启动前 `repair()` 对齐 history |
| 仍无法启动 | 删库重建（Beta 推荐）或执行 `scripts/deploy/templates/repair-flyway-admin.sql` |

**生产**出现上述错误 → **禁止**开 out-of-order；应走发布流程：只追加新 V*，或 DBA 评审后手工对齐。

---

## 5. 新环境 / 部署

```text
CREATE DATABASE zestflow_admin ...
配置 spring.datasource + spring.flyway.enabled=true
启动 Admin → Flyway migrate V1…V6
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
- 版本号无重复

---

*详见 `db/migration/README.md` 快速索引。*
