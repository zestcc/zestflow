-- zestflow_admin 开发库 Rebaseline（2026-06-08：V1→V2→V3 连续链）
-- 执行前请备份。适用于旧 Beta history（V4/V5/V6 或旧 V2 beta align）与 jar 不一致。
USE zestflow_admin;

DELETE FROM flyway_schema_history;

SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 随后重启 Admin（非 prod）：Flyway 重放 V1→V3（脚本幂等，无需删表）
