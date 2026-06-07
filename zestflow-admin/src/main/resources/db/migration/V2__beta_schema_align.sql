-- Flyway V2 — 存量库对齐 Beta 单轨 V1（V1 仅 CREATE IF NOT EXISTS，已有表不会自动补列/建新表）
-- 2026-06-07：合并冲突后，旧开发库执行本脚本即可与最新 jar 对齐

-- ==================== sys_dict_data 树形/级联 ====================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND COLUMN_NAME = 'parent_id');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `sys_dict_data` ADD COLUMN `parent_id` BIGINT DEFAULT NULL COMMENT ''父级数据项ID（同类型树）'' AFTER `type_code`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND COLUMN_NAME = 'parent_type_code');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `sys_dict_data` ADD COLUMN `parent_type_code` VARCHAR(64) DEFAULT NULL COMMENT ''父级字典类型（空=同类型）'' AFTER `parent_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND COLUMN_NAME = 'parent_value');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `sys_dict_data` ADD COLUMN `parent_value` VARCHAR(128) DEFAULT NULL COMMENT ''父级字典项 value'' AFTER `parent_type_code`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND COLUMN_NAME = 'extra');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `sys_dict_data` ADD COLUMN `extra` TEXT DEFAULT NULL COMMENT ''扩展 JSON'' AFTER `remark`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND INDEX_NAME = 'idx_dict_parent');
SET @ddl = IF(@idx_exists = 0, 'ALTER TABLE `sys_dict_data` ADD KEY `idx_dict_parent` (`tenant_id`, `type_code`, `parent_type_code`, `parent_value`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_dict_data' AND INDEX_NAME = 'idx_dict_parent_id');
SET @ddl = IF(@idx_exists = 0, 'ALTER TABLE `sys_dict_data` ADD KEY `idx_dict_parent_id` (`tenant_id`, `type_code`, `parent_id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== sys_config ====================
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key`   VARCHAR(128) NOT NULL                COMMENT '配置键（租户内唯一）',
    `config_name`  VARCHAR(128) NOT NULL                COMMENT '配置名称',
    `config_value` TEXT         DEFAULT NULL            COMMENT '配置值（JSON 或文本）',
    `value_type`   VARCHAR(16)  DEFAULT 'json'          COMMENT '值类型 json/text/number/bool',
    `category`     VARCHAR(64)  DEFAULT 'system'       COMMENT '分类',
    `status`       TINYINT      DEFAULT 1               COMMENT '0停用 1启用',
    `sort`         INT          DEFAULT 0               COMMENT '排序',
    `remark`       VARCHAR(256) DEFAULT NULL            COMMENT '备注',
    `tenant_id`    BIGINT       DEFAULT 1               COMMENT '租户ID',
    `created_by`   VARCHAR(64)  DEFAULT NULL,
    `updated_by`   VARCHAR(64)  DEFAULT NULL,
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_config` (`tenant_id`, `config_key`),
    KEY `idx_sys_config_category` (`tenant_id`, `category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置（键值/JSON，与字典枚举分离）';

-- ==================== executor_registry ====================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'executor_registry' AND COLUMN_NAME = 'declared_chain_keys');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `executor_registry` ADD COLUMN `declared_chain_keys` TEXT DEFAULT NULL COMMENT ''@ZestChain 声明的 chain_key 列表 JSON'' AFTER `last_heartbeat`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== schedule / schedule_log ====================
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'job_type');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` MODIFY COLUMN `chain_id` BIGINT NULL COMMENT ''关联链ID（平台任务可为空）'', MODIFY COLUMN `chain_code` VARCHAR(128) NULL COMMENT ''链编码或平台任务键''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'job_type');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `job_type` VARCHAR(16) NOT NULL DEFAULT ''CHAIN'' COMMENT ''CHAIN-业务链 PLATFORM-平台任务'' AFTER `chain_name`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'job_key');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `job_key` VARCHAR(64) NULL COMMENT ''平台任务唯一键'' AFTER `job_type`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'schedule_kind');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `schedule_kind` VARCHAR(16) NOT NULL DEFAULT ''CRON'' COMMENT ''CRON|FIXED_RATE|FIXED_DELAY'' AFTER `cron`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'fixed_interval_ms');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `fixed_interval_ms` BIGINT NULL COMMENT ''固定间隔毫秒（非 CRON 时使用）'' AFTER `schedule_kind`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'module');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `module` VARCHAR(32) NULL COMMENT ''所属模块 admin|executor|collector'' AFTER `fixed_interval_ms`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'editable');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `editable` TINYINT NOT NULL DEFAULT 1 COMMENT ''是否允许编辑/删除'' AFTER `module`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'remote');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `remote` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否节点本地执行（仅元数据展示）'' AFTER `editable`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND COLUMN_NAME = 'last_trigger_at');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule` ADD COLUMN `last_trigger_at` DATETIME NULL COMMENT ''最近触发时间'' AFTER `remote`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule' AND INDEX_NAME = 'uk_job_key');
SET @ddl = IF(@idx_exists = 0, 'ALTER TABLE `schedule` ADD UNIQUE KEY `uk_job_key` (`job_key`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_log' AND COLUMN_NAME = 'job_key');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule_log` MODIFY COLUMN `chain_code` VARCHAR(128) NULL COMMENT ''链编码或平台任务键''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_log' AND COLUMN_NAME = 'job_key');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule_log` ADD COLUMN `job_key` VARCHAR(64) NULL COMMENT ''平台任务键'' AFTER `schedule_id`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_log' AND COLUMN_NAME = 'job_name');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule_log` ADD COLUMN `job_name` VARCHAR(128) NULL COMMENT ''任务名称'' AFTER `job_key`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_log' AND INDEX_NAME = 'idx_job_key');
SET @ddl = IF(@idx_exists = 0, 'ALTER TABLE `schedule_log` ADD KEY `idx_job_key` (`job_key`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_log' AND COLUMN_NAME = 'execution_id');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `schedule_log` ADD COLUMN `execution_id` VARCHAR(64) DEFAULT NULL COMMENT ''链执行追踪 ID（CHAIN 调度触发后回写）'' AFTER `executor_address`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_log' AND INDEX_NAME = 'idx_schedule_log_execution_id');
SET @ddl = IF(@idx_exists = 0, 'ALTER TABLE `schedule_log` ADD KEY `idx_schedule_log_execution_id` (`execution_id`)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== AI Copilot（表 + 补列） ====================
CREATE TABLE IF NOT EXISTS `zf_ai_tenant_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL                COMMENT '租户ID',
    `enabled`          TINYINT      DEFAULT 0               COMMENT '是否启用 Copilot',
    `preset`           VARCHAR(50)  DEFAULT 'deepseek'      COMMENT '提供商预设 ID',
    `base_url`         VARCHAR(512) DEFAULT NULL            COMMENT '覆盖 baseUrl',
    `api_key_enc`      VARCHAR(1024) DEFAULT NULL           COMMENT '加密 API Key',
    `model`            VARCHAR(100) DEFAULT NULL            COMMENT '覆盖模型名',
    `allowed_presets`  VARCHAR(512) DEFAULT NULL            COMMENT '允许的预设 JSON 数组',
    `monthly_token_quota` INT       DEFAULT NULL            COMMENT '月 Token 估算上限，NULL=不限',
    `created_by`       VARCHAR(64)  DEFAULT NULL,
    `updated_by`       VARCHAR(64)  DEFAULT NULL,
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`       TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 AI 配置';

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zf_ai_tenant_config' AND COLUMN_NAME = 'monthly_token_quota');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `zf_ai_tenant_config` ADD COLUMN `monthly_token_quota` INT DEFAULT NULL COMMENT ''月 Token 估算上限，NULL=不限'' AFTER `allowed_presets`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zf_ai_copilot_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT       NOT NULL                COMMENT '租户ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '用户ID',
    `app_code`      VARCHAR(50)  DEFAULT NULL            COMMENT '应用编码',
    `design_id`     VARCHAR(64)  DEFAULT NULL            COMMENT '设计编码',
    `chain_code`    VARCHAR(64)  DEFAULT NULL            COMMENT '链编码',
    `mode`          VARCHAR(32)  DEFAULT NULL            COMMENT 'explain|suggest|expression|diagnose|scaffold',
    `adopted`       TINYINT      DEFAULT NULL            COMMENT '1采纳 0拒绝',
    `latency_ms`    INT          DEFAULT NULL            COMMENT 'LLM 调用耗时 ms',
    `success`       TINYINT      DEFAULT 1               COMMENT '1成功 0失败',
    `error_message` VARCHAR(500) DEFAULT NULL            COMMENT '失败摘要',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_session_tenant_user` (`tenant_id`, `user_id`),
    KEY `idx_ai_session_design` (`tenant_id`, `design_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Copilot 会话';

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'latency_ms');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `latency_ms` INT DEFAULT NULL COMMENT ''LLM 调用耗时 ms'' AFTER `adopted`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'success');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `success` TINYINT DEFAULT 1 COMMENT ''1成功 0失败'' AFTER `latency_ms`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'error_message');
SET @ddl = IF(@col_exists = 0, 'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `error_message` VARCHAR(500) DEFAULT NULL COMMENT ''失败摘要'' AFTER `success`', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS `zf_ai_copilot_message` (
    `id`               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_id`       BIGINT        NOT NULL                COMMENT '会话ID',
    `tenant_id`        BIGINT        NOT NULL                COMMENT '租户ID',
    `role`             VARCHAR(16)   NOT NULL                COMMENT 'user|assistant|system',
    `content_summary`  VARCHAR(2000) DEFAULT NULL            COMMENT '内容摘要',
    `token_estimate`   INT           DEFAULT NULL,
    `created_at`       DATETIME      DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_message_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Copilot 消息审计';

CREATE TABLE IF NOT EXISTS `zf_ai_chain_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT       NOT NULL                COMMENT '租户ID',
    `name`            VARCHAR(128) NOT NULL                COMMENT '模板名称',
    `description`     VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `app_code`        VARCHAR(50)  DEFAULT NULL            COMMENT '应用编码',
    `prompt_summary`  VARCHAR(500) DEFAULT NULL            COMMENT '原始需求摘要',
    `chain_data`      MEDIUMTEXT   NOT NULL                COMMENT '链定义 JSON',
    `tags`            VARCHAR(256) DEFAULT NULL            COMMENT '标签 JSON 数组',
    `created_by`      VARCHAR(64)  DEFAULT NULL,
    `updated_by`      VARCHAR(64)  DEFAULT NULL,
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_ai_tpl_tenant_app` (`tenant_id`, `app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 链模板库';

CREATE TABLE IF NOT EXISTS `zf_ai_rag_document` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    BIGINT        NOT NULL                COMMENT '租户ID',
    `title`        VARCHAR(200)  NOT NULL                COMMENT '文档标题',
    `app_code`     VARCHAR(50)   DEFAULT NULL            COMMENT '可选应用范围，空=租户全局',
    `content`      MEDIUMTEXT    NOT NULL                COMMENT 'Markdown 正文',
    `enabled`      TINYINT       DEFAULT 1               COMMENT '1启用 0禁用',
    `sort_order`   INT           DEFAULT 0               COMMENT '排序',
    `source_type`  VARCHAR(16)   DEFAULT 'upload'        COMMENT 'upload|filesystem',
    `created_by`   VARCHAR(64)   DEFAULT NULL,
    `updated_by`   VARCHAR(64)   DEFAULT NULL,
    `created_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_ai_rag_doc_tenant` (`tenant_id`, `enabled`, `is_deleted`),
    KEY `idx_ai_rag_doc_app` (`tenant_id`, `app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 AI RAG 文档';

-- ==================== SLA 告警 ====================
CREATE TABLE IF NOT EXISTS `alert_cooldown` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `alert_key`    VARCHAR(192) NOT NULL                COMMENT '告警键 tenant:app:rule',
    `last_sent_at` DATETIME     NOT NULL                COMMENT '上次发送时间',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_key` (`alert_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA 告警冷却';

CREATE TABLE IF NOT EXISTS `alert_tenant_config` (
    `id`                        BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`                 BIGINT         NOT NULL                COMMENT '租户 ID',
    `enabled`                   TINYINT        NULL                    COMMENT '总开关，NULL=沿用 yml 默认',
    `cooldown_minutes`          INT            NULL                    COMMENT '冷却分钟',
    `window_minutes`            INT            NULL                    COMMENT '统计窗口分钟',
    `min_executions`            INT            NULL                    COMMENT '最少执行次数',
    `success_rate_threshold`    DECIMAL(5,2)   NULL                    COMMENT '成功率阈值',
    `fail_count_threshold`      INT            NULL                    COMMENT '失败次数阈值',
    `p95_cost_ms_threshold`     BIGINT         NULL                    COMMENT 'P95 毫秒阈值',
    `schedule_fail_threshold`   INT            NULL                    COMMENT '调度失败阈值',
    `alert_no_online_executor`  TINYINT        NULL                    COMMENT '无在线执行器告警',
    `subject_prefix`            VARCHAR(128)   NULL                    COMMENT '邮件主题前缀',
    `created_at`                DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA 告警租户配置';

CREATE TABLE IF NOT EXISTS `alert_history` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL                COMMENT '租户 ID',
    `app_code`         VARCHAR(64)  NOT NULL                COMMENT '应用模块',
    `rule_code`        VARCHAR(64)  NOT NULL                COMMENT '规则编码',
    `rule_label`       VARCHAR(128) NOT NULL                COMMENT '规则名称',
    `summary`          VARCHAR(512) NOT NULL                COMMENT '摘要',
    `metrics_json`     TEXT         NULL                    COMMENT '指标 JSON',
    `recipient_count`  INT          NOT NULL DEFAULT 0      COMMENT '收件人数',
    `recipients`       VARCHAR(1024) NULL                   COMMENT '脱敏收件人列表',
    `mail_sent`        TINYINT      NOT NULL DEFAULT 1      COMMENT '1=真实发信 0=仅日志',
    `sent_at`          DATETIME     NOT NULL                COMMENT '发送时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_alert_history_tenant_sent` (`tenant_id`, `sent_at`),
    KEY `idx_alert_history_app_sent` (`tenant_id`, `app_code`, `sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA 告警历史';
