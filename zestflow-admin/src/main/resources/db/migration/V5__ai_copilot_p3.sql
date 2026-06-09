-- 2026-06-08：Copilot P3 — 会话标题/归档、异步 Job、Trace 步骤（幂等：可重复执行）

SET @zf_db = DATABASE();

SET @zf_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @zf_db AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'title');
SET @zf_sql = IF(@zf_cnt = 0,
    'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `title` VARCHAR(200) DEFAULT NULL COMMENT ''会话标题'' AFTER `chain_code`',
    'SELECT 1');
PREPARE zf_stmt FROM @zf_sql;
EXECUTE zf_stmt;
DEALLOCATE PREPARE zf_stmt;

SET @zf_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @zf_db AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'archived');
SET @zf_sql = IF(@zf_cnt = 0,
    'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `archived` TINYINT DEFAULT 0 COMMENT ''1已归档'' AFTER `last_model`',
    'SELECT 1');
PREPARE zf_stmt FROM @zf_sql;
EXECUTE zf_stmt;
DEALLOCATE PREPARE zf_stmt;

CREATE TABLE IF NOT EXISTS `zf_ai_copilot_job` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT        NOT NULL                COMMENT '租户ID',
    `user_id`          BIGINT        NOT NULL                COMMENT '用户ID',
    `session_id`       BIGINT        DEFAULT NULL            COMMENT '关联会话ID',
    `job_type`         VARCHAR(32)   NOT NULL                COMMENT 'suggest|explain',
    `status`           VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|RUNNING|DONE|FAILED|CANCELLED',
    `request_json`     TEXT          DEFAULT NULL            COMMENT '请求 JSON',
    `result_json`      MEDIUMTEXT    DEFAULT NULL            COMMENT '结果 JSON',
    `progress_step`    VARCHAR(500)  DEFAULT NULL            COMMENT '当前步骤',
    `reasoning_buffer` MEDIUMTEXT    DEFAULT NULL            COMMENT '思考过程累积（轮询用）',
    `error_message`    VARCHAR(1000) DEFAULT NULL            COMMENT '失败摘要',
    `latency_ms`       INT           DEFAULT NULL            COMMENT '总耗时 ms',
    `created_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `finished_at`      DATETIME      DEFAULT NULL            COMMENT '完成时间',
    PRIMARY KEY (`id`),
    KEY `idx_ai_job_tenant_user` (`tenant_id`, `user_id`),
    KEY `idx_ai_job_session` (`session_id`),
    KEY `idx_ai_job_status` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Copilot 异步任务';

CREATE TABLE IF NOT EXISTS `zf_ai_copilot_trace_step` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`      BIGINT        NOT NULL                COMMENT '租户ID',
    `session_id`     BIGINT        NOT NULL                COMMENT '会话ID',
    `job_id`         BIGINT        DEFAULT NULL            COMMENT '异步任务ID',
    `step_type`      VARCHAR(32)   NOT NULL                COMMENT 'RAG|LLM|QUALITY|VALIDATE|REPAIR|DONE',
    `step_name`      VARCHAR(200)  NOT NULL                COMMENT '步骤名称',
    `status`         VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING|OK|FAIL',
    `latency_ms`     INT           DEFAULT NULL            COMMENT '步骤耗时 ms',
    `token_estimate` INT           DEFAULT NULL            COMMENT 'Token 估算',
    `detail_json`    TEXT          DEFAULT NULL            COMMENT '扩展详情 JSON',
    `sort_order`     INT           DEFAULT 0               COMMENT '排序',
    `created_at`     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_trace_session` (`session_id`, `sort_order`),
    KEY `idx_ai_trace_job` (`job_id`),
    KEY `idx_ai_trace_tenant_time` (`tenant_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Copilot Trace 步骤';
