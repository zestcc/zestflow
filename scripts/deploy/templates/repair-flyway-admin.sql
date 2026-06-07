-- zestflow_admin：Flyway history 修复（执行前请备份）
-- 2026-06-08 Rebaseline 后 jar 内为 V1→V2→V3 连续链
USE zestflow_admin;

-- 1) 清空 history（非 prod 启动也会自动做；手工执行后重启 Admin）
DELETE FROM flyway_schema_history;

-- 2) 若表结构极老（缺 V1 列/表），请删库重建：
--    DROP DATABASE zestflow_admin;
--    CREATE DATABASE zestflow_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
