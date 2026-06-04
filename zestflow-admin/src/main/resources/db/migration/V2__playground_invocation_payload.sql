-- 2026-06-04：试验场 request/response 迁移至 app_log.invocation_payload
ALTER TABLE `playground_record`
    ADD COLUMN `invocation_id` VARCHAR(64) DEFAULT NULL COMMENT '调用载荷 ID（request/response 存 app_log）' AFTER `body_type`,
    DROP COLUMN `request_body`,
    DROP COLUMN `response_body`,
    ADD KEY `idx_invocation_id` (`invocation_id`);
