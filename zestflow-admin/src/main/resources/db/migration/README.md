# Flyway 迁移（zestflow_admin）

> **完整策略**见 [FLYWAY_POLICY.md](../../docs/FLYWAY_POLICY.md)（分层、铁律、故障处理）。

## 快速命令

```powershell
# 本地 Admin 启动前（MySQL 已建 zestflow_admin）
# application-local.yml: spring.flyway.enabled=true
# 非 prod 自动 repair + out-of-order migrate
```

## 下一版本

当前最新 **V6** → 下一增量脚本 **`V7__描述.sql`**

## 删库重建（Beta 开发）

```sql
DROP DATABASE zestflow_admin;
CREATE DATABASE zestflow_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```
