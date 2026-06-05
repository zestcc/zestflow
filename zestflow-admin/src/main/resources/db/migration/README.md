# Flyway 迁移（zestflow_admin）

## 原则

| 规则 | 说明 |
|------|------|
| **单轨** | 表结构只由 `db/migration/V*.sql` 维护 |
| **库自行创建** | 部署/开发前手工 `CREATE DATABASE zestflow_admin`，无 init-db 脚本 |
| **不可改历史** | 已发布的 `V1__*.sql` 禁止修改 |
| **增量只加文件** | 新 DDL 写 `V2__描述.sql` |

## 部署 / 开发流程

```text
MySQL: CREATE DATABASE zestflow_admin ...
config/application-prod.yml  →  spring.datasource.url / username / password
start-admin                    →  Flyway 自动 migrate
```

## 已有库（曾手工建过表）

`baseline-on-migrate: true` + `baseline-version: 1`（prod 已配置）：有表无 history 时 baseline，不重复建表。

## 故障排查

| 现象 | 处理 |
|------|------|
| `Access denied for user 'root'` | 改 **application-prod.yml** 的 `spring.datasource.password`（与 db.env 无关） |
| `Unknown database 'zestflow_admin'` | 先手工建库 |
| `checksum mismatch` | 勿改已发布 V*；dev 可 `flyway repair` |
