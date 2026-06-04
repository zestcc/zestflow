-- 2026-06-04：可观测性改造迁库脚本（app_log + admin）
-- 幂等：可重复执行

-- ==================== zestflow_app_log ====================
CREATE DATABASE IF NOT EXISTS `zestflow_app_log` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `zestflow_app_log`;

-- 新建载荷表（若不存在）
CREATE TABLE IF NOT EXISTS `chain_event_payload` (
    `event_id`       VARCHAR(64)  NOT NULL PRIMARY KEY     COMMENT '事件 ID，关联 chain_event.event_id',
    `params`         MEDIUMTEXT   DEFAULT NULL             COMMENT '入参',
    `result`         MEDIUMTEXT   DEFAULT NULL             COMMENT '出参',
    `error_message`  TEXT         DEFAULT NULL             COMMENT '错误消息'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链事件载荷表';

CREATE TABLE IF NOT EXISTS `invocation_payload` (
    `invocation_id`   VARCHAR(64)  NOT NULL PRIMARY KEY    COMMENT '调用唯一 ID',
    `source_type`     VARCHAR(32)  NOT NULL                COMMENT 'PLAYGROUND/SCHEDULE/API',
    `execution_id`    VARCHAR(64)  DEFAULT NULL            COMMENT '关联链 execution_id',
    `scene_code`      VARCHAR(64)  DEFAULT NULL            COMMENT '试验场场景编码',
    `request_body`    MEDIUMTEXT   DEFAULT NULL            COMMENT '请求体',
    `response_body`   MEDIUMTEXT   DEFAULT NULL            COMMENT '响应体',
    `request_headers` TEXT         DEFAULT NULL            COMMENT '请求头 JSON',
    `tenant_id`       BIGINT       DEFAULT 1               COMMENT '租户ID',
    `app_code`        VARCHAR(50)  DEFAULT NULL            COMMENT '应用编码',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_scene_code` (`scene_code`),
    KEY `idx_tenant_app` (`tenant_id`, `app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部调用载荷表';

-- 存量 chain_event 大字段迁移到 payload（仅当旧列仍存在时）
SET @has_params := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_app_log' AND TABLE_NAME = 'chain_event' AND COLUMN_NAME = 'params'
);
SET @migrate_sql := IF(@has_params > 0,
    'INSERT IGNORE INTO chain_event_payload(event_id, params, result, error_message)
     SELECT event_id, params, result, error_message FROM chain_event
     WHERE (params IS NOT NULL AND params != '''')
        OR (result IS NOT NULL AND result != '''')
        OR (error_message IS NOT NULL AND error_message != '''')',
    'SELECT 1');
PREPARE stmt FROM @migrate_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 删除 chain_event 大字段列（若存在）
SET @drop_params := IF(@has_params > 0, 'ALTER TABLE chain_event DROP COLUMN params', 'SELECT 1');
PREPARE stmt FROM @drop_params; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_result := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_app_log' AND TABLE_NAME = 'chain_event' AND COLUMN_NAME = 'result'
);
SET @drop_result := IF(@has_result > 0, 'ALTER TABLE chain_event DROP COLUMN result', 'SELECT 1');
PREPARE stmt FROM @drop_result; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_err := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_app_log' AND TABLE_NAME = 'chain_event' AND COLUMN_NAME = 'error_message'
);
SET @drop_err := IF(@has_err > 0, 'ALTER TABLE chain_event DROP COLUMN error_message', 'SELECT 1');
PREPARE stmt FROM @drop_err; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== zestflow_admin ====================
USE `zestflow_admin`;

SET @has_inv := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_admin' AND TABLE_NAME = 'playground_record' AND COLUMN_NAME = 'invocation_id'
);
SET @add_inv := IF(@has_inv = 0,
    'ALTER TABLE playground_record ADD COLUMN invocation_id VARCHAR(64) DEFAULT NULL COMMENT ''调用载荷 ID'' AFTER body_type',
    'SELECT 1');
PREPARE stmt FROM @add_inv; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_req := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_admin' AND TABLE_NAME = 'playground_record' AND COLUMN_NAME = 'request_body'
);
SET @drop_req := IF(@has_req > 0, 'ALTER TABLE playground_record DROP COLUMN request_body', 'SELECT 1');
PREPARE stmt FROM @drop_req; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_resp := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_admin' AND TABLE_NAME = 'playground_record' AND COLUMN_NAME = 'response_body'
);
SET @drop_resp := IF(@has_resp > 0, 'ALTER TABLE playground_record DROP COLUMN response_body', 'SELECT 1');
PREPARE stmt FROM @drop_resp; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'zestflow_admin' AND TABLE_NAME = 'playground_record' AND INDEX_NAME = 'idx_invocation_id'
);
SET @add_idx := IF(@has_idx = 0,
    'ALTER TABLE playground_record ADD KEY idx_invocation_id (invocation_id)',
    'SELECT 1');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SELECT 'migrate-observability done' AS status;
