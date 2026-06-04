-- 2026-06-04：IP 试玩 V1 — 每 IP 唯一映射 + 场景编码按租户隔离
-- 已有库手动执行（Flyway 默认关闭）；新库请直接用 init.sql

USE `zestflow_admin`;

-- tenant_ip_mapping：每 IP 仅一条映射（防并发双建）
ALTER TABLE `tenant_ip_mapping`
    ADD UNIQUE KEY `uk_ip_address` (`ip_address`);

-- playground_scene：scene_code 在租户内唯一（支持克隆母版场景）
ALTER TABLE `playground_scene`
    DROP INDEX `uk_scene_code`,
    ADD UNIQUE KEY `uk_tenant_scene` (`tenant_id`, `scene_code`);
