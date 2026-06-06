# Flyway 迁移（zestflow_admin）

## 原则

| 规则 | 说明 |
|------|------|
| **单轨** | 表结构只由 `db/migration/V*.sql` 维护 |
| **库自行创建** | 部署/开发前手工 `CREATE DATABASE zestflow_admin`，无 init-db 脚本 |
| **幂等** | V1 用 `CREATE TABLE IF NOT EXISTS`；V2+ 列/索引用「存在则跳过」 |
| **不可改历史** | 已发布的 V* 改内容会 checksum 冲突；dev 可 `flyway repair` |
| **增量只加文件** | 新 DDL 写 `V{n}__描述.sql` |

## 部署 / 开发流程

```text
MySQL: CREATE DATABASE zestflow_admin ...
config/application-prod.yml  →  spring.datasource.url / username / password
start-admin                    →  Flyway 自动 migrate
```

## 已有库（曾手工建过表）

`baseline-on-migrate: true` + `baseline-version: 1`（prod 已配置）：有表无 history 时 baseline。

V1 已 `IF NOT EXISTS`，即使未 baseline、直接跑 migrate 也不会因「表已存在」失败。

## 故障排查

| 现象 | 处理 |
|------|------|
| `Access denied for user 'root'` | 改 **application-prod.yml** 的 `spring.datasource.password`（与 db.env 无关） |
| `Unknown database 'zestflow_admin'` | 先手工建库 |
| `Duplicate column 'declared_chain_keys'` | 已修复：V2 为幂等；若 history 卡在失败态，删失败行或 `flyway repair` 后重启 |
| `checksum mismatch` | 勿改已发布 V*；dev 可 `flyway repair` |
| 线上 `flyway.enabled: false` | 表结构不会自动升级，与 jar 内 V* 脱节；demo/prod 建议 `enabled: true` |
