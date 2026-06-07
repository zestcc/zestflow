-- zestflow_admin：Beta 整合后修复 Flyway history（执行前请备份）
-- 场景：原 V2–V7 已合并进 V1，旧 history 与 jar 不一致
USE zestflow_admin;

-- 1) 删除 V2+ 历史记录（整合后 jar 内仅有 V1）
DELETE FROM flyway_schema_history WHERE version > '1';

-- 2) 删除失败记录
DELETE FROM flyway_schema_history WHERE success = 0;

-- 3) 启动 Admin 后 Flyway 会 repair V1 checksum（demo 环境自动 repair+migrate）
--    若表结构仍是旧版，请删库重建：
--    DROP DATABASE zestflow_admin;
--    CREATE DATABASE zestflow_admin CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

SELECT installed_rank, version, description, type, success, checksum
FROM flyway_schema_history
ORDER BY installed_rank;
