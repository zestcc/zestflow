# Flyway 迁移（zestflow_admin）

## 原则

| 规则 | 说明 |
|------|------|
| **单轨** | 表结构只由 `db/migration/V*.sql` 维护 |
| **库自行创建** | 部署/开发前手工 `CREATE DATABASE zestflow_admin`，无 init-db 脚本 |
| **幂等** | V1 用 `CREATE TABLE IF NOT EXISTS`；V2+ 列/索引用「存在则跳过」 |
| **Beta 整合** | 2026-06-06 已将原 V2–V7 及字典级联/sys_config 合并进 V1；新环境只需跑 V1 |
| **存量库对齐** | 2026-06-07 新增 `V2__beta_schema_align.sql`，幂等补列/建新表（旧 V1 库必跑） |
| **增量只加文件** | 新 DDL 写 **下一未占用版本号**（当前最新 **V6**，下一增量用 **V7**） |

## 版本清单

| 版本 | 文件 | 说明 |
|------|------|------|
| V1 | `V1__init_admin_schema.sql` | 基线 schema（含 AI Copilot 表） |
| V2 | `V2__beta_schema_align.sql` | 存量库幂等对齐 |
| V4 | `V4__schedule_hub_embedded.sql` | 停用 Admin 侧 schedule.scan Cron |
| V5 | `V5__registry_event_sla_collector.sql` | 注册/SLA 平台任务迁移至 Collector |
| V6 | `V6__ai_learning_event.sql` | AI Chain-first 学习事件表 `zf_ai_learning_event` |

> **V3 预留未用**：远程调度改造直接发布 V4/V5；学习事件后续补为 **V6**，避免已跑 V4/V5 的库出现「倒序插入 V3」问题。

## 部署 / 开发流程

```text
MySQL: CREATE DATABASE zestflow_admin ...
config/application-prod.yml  →  spring.datasource.url / username / password
start-admin                    →  Flyway 自动 migrate（V1 → V2 → V4 → V5 → V6）
```

## 已有库（曾跑过旧 V2+ / 内测 V3/V4）

Beta 阶段推荐 **删库重建**（最简单）：

```sql
DROP DATABASE zestflow_admin;
CREATE DATABASE zestflow_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

若需保留数据，手动对齐表结构后清理 history（内测 V3/V4 已并入 V1，需删掉 >1 的记录并 repair V1 checksum）：

```sql
DELETE FROM flyway_schema_history WHERE version > '1';
-- 启动时 flyway repair（demo 环境自动 repair+migrate）
```

`baseline-on-migrate: true` + `baseline-version: 1`（prod 已配置）：有表无 history 时 baseline。

V1 已 `IF NOT EXISTS`，即使未 baseline、直接跑 migrate 也不会因「表已存在」失败；但 **旧表缺列** 时需删库或手工 ALTER。

## 故障排查

| 现象 | 处理 |
|------|------|
| `Access denied for user 'root'` | 改 **application-prod.yml** 的 `spring.datasource.password`（与 db.env 无关） |
| `Unknown database 'zestflow_admin'` | 先手工建库 |
| `Found more than one migration with version X` | 勿重复版本号；执行 `mvn clean compile` 清 target 缓存 |
| `checksum mismatch` | 非 prod 启动会自动 repair；或跑 repair-flyway-admin.sql |
| `Unknown column`（字典 parent/extra 等） | 跑 V2 对齐脚本；或删库重建 |
| 线上 `flyway.enabled: false` | 表结构不会自动升级，与 jar 内 V* 脱节；demo/prod 建议 `enabled: true` |
| 已跑 V4/V5 缺 V6 | 正常：启动 Admin 自动补跑 V6 |
