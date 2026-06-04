-- 2026-06-04：可观测性改造迁库脚本（app_log + admin）
-- 幂等：可重复执行

-- ==================== zestflow_app_log ====================
CREATE DATABASE IF NOT EXISTS `zestflow_app_log` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `zestflow_app_log`;

-- 统一载荷表
CREATE TABLE IF NOT EXISTS `execution_payload` (
    `ref_id`          VARCHAR(64)  NOT NULL PRIMARY KEY     COMMENT '关联 ID：chain_event.event_id 或 invocation_id',
    `ref_type`        VARCHAR(16)  NOT NULL                 COMMENT 'CHAIN_EVENT | INVOCATION',
    `execution_id`    VARCHAR(64)  DEFAULT NULL             COMMENT '链 execution_id（可选）',
    `source_type`     VARCHAR(32)  DEFAULT NULL             COMMENT 'PLAYGROUND/SCHEDULE/API（仅 INVOCATION）',
    `scene_code`      VARCHAR(64)  DEFAULT NULL             COMMENT '试验场场景编码',
    `params`          MEDIUMTEXT   DEFAULT NULL             COMMENT '入参 / 请求体',
    `result`          MEDIUMTEXT   DEFAULT NULL             COMMENT '出参 / 响应体',
    `error_message`   TEXT         DEFAULT NULL             COMMENT '错误消息',
    `extra`           TEXT         DEFAULT NULL             COMMENT '扩展 JSON（如 request_headers）',
    `tenant_id`       BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`        VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_ref_type` (`ref_type`),
    KEY `idx_scene_code` (`scene_code`),
    KEY `idx_tenant_app` (`tenant_id`, `app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行载荷统一表';

-- 从旧 chain_event_payload 迁移（若存在）
SET @has_cep := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'zestflow_app_log' AND TABLE_NAME = 'chain_event_payload'
);
SET @migrate_cep := IF(@has_cep > 0,
    'INSERT IGNORE INTO execution_payload(ref_id, ref_type, params, result, error_message)
     SELECT event_id, ''CHAIN_EVENT'', params, result, error_message FROM chain_event_payload',
    'SELECT 1');
PREPARE stmt FROM @migrate_cep; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 从旧 invocation_payload 迁移（若存在）
SET @has_ip := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'zestflow_app_log' AND TABLE_NAME = 'invocation_payload'
);
SET @migrate_ip := IF(@has_ip > 0,
    'INSERT IGNORE INTO execution_payload(ref_id, ref_type, execution_id, source_type, scene_code, params, result, extra, tenant_id, app_code, created_at)
     SELECT invocation_id, ''INVOCATION'', execution_id, source_type, scene_code, request_body, response_body, request_headers, tenant_id, app_code, created_at FROM invocation_payload',
    'SELECT 1');
PREPARE stmt FROM @migrate_ip; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_cep := IF(@has_cep > 0, 'DROP TABLE chain_event_payload', 'SELECT 1');
PREPARE stmt FROM @drop_cep; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @drop_ip := IF(@has_ip > 0, 'DROP TABLE invocation_payload', 'SELECT 1');
PREPARE stmt FROM @drop_ip; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 存量 chain_event 大字段迁移到 execution_payload（仅当旧列仍存在时）
SET @has_params := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'zestflow_app_log' AND TABLE_NAME = 'chain_event' AND COLUMN_NAME = 'params'
);
SET @migrate_sql := IF(@has_params > 0,
    'INSERT IGNORE INTO execution_payload(ref_id, ref_type, params, result, error_message)
     SELECT event_id, ''CHAIN_EVENT'', params, result, error_message FROM chain_event
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
