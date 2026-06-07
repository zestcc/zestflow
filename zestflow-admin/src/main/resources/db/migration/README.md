# Flyway 迁移（zestflow_admin）

> **完整策略**见 [FLYWAY_POLICY.md](../../../docs/FLYWAY_POLICY.md)（Rebaseline、分层、故障处理）。

## 当前链（2026-06-08）

`V1` 全量 schema → `V2` 平台调度 v0.2 → `V3` AI 学习事件

## 快速命令

```powershell
# 本地 Admin 启动前（MySQL 已建 zestflow_admin）
# application-local.yml: spring.flyway.enabled=true
# 非 prod：legacy history 自动清理 + repair + migrate
```

## 下一版本

当前最新 **V3** → 下一增量 **`V4__描述.sql`**

## 开发库 Rebaseline

```powershell
.\scripts\deploy\rebaseline-admin-dev.ps1
```

或删库重建：

```sql
DROP DATABASE zestflow_admin;
CREATE DATABASE zestflow_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
