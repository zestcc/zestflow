-- zestflow_admin：修复 Flyway history（试玩/开发库；执行前请备份）
-- 场景：升级 jar 后 V1 checksum 变化，或 V2 曾失败留下 success=0 记录
USE zestflow_admin;

-- 1) 对齐 V1 checksum（与当前 jar 内 V1__init_admin_schema.sql 一致，2026-06-06 幂等版）
UPDATE flyway_schema_history
SET checksum = 607363600
WHERE version = '1';

-- 2) 删除失败的迁移记录，下次启动会重跑（V2 已幂等）
DELETE FROM flyway_schema_history
WHERE success = 0;

-- 3) 确认
SELECT installed_rank, version, description, type, success, checksum
FROM flyway_schema_history
ORDER BY installed_rank;
