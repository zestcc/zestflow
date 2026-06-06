-- Flyway V3 — AI Copilot 租户配置与会话审计
-- 2026-06-02

CREATE TABLE IF NOT EXISTS `zf_ai_tenant_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL                COMMENT '租户ID',
    `enabled`          TINYINT      DEFAULT 0               COMMENT '是否启用 Copilot',
    `preset`           VARCHAR(50)  DEFAULT 'deepseek'      COMMENT '提供商预设 ID',
    `base_url`         VARCHAR(512) DEFAULT NULL            COMMENT '覆盖 baseUrl',
    `api_key_enc`      VARCHAR(1024) DEFAULT NULL           COMMENT '加密 API Key',
    `model`            VARCHAR(100) DEFAULT NULL            COMMENT '覆盖模型名',
    `allowed_presets`  VARCHAR(512) DEFAULT NULL            COMMENT '允许的预设 JSON 数组',
    `created_by`       VARCHAR(64)  DEFAULT NULL,
    `updated_by`       VARCHAR(64)  DEFAULT NULL,
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`       TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 AI 配置';

CREATE TABLE IF NOT EXISTS `zf_ai_copilot_session` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`     BIGINT       NOT NULL                COMMENT '租户ID',
    `user_id`       BIGINT       NOT NULL                COMMENT '用户ID',
    `app_code`      VARCHAR(50)  DEFAULT NULL            COMMENT '应用编码',
    `design_id`     VARCHAR(64)  DEFAULT NULL            COMMENT '设计编码',
    `chain_code`    VARCHAR(64)  DEFAULT NULL            COMMENT '链编码',
    `mode`          VARCHAR(32)  DEFAULT NULL            COMMENT 'explain|suggest|expression|diagnose|scaffold',
    `adopted`       TINYINT      DEFAULT NULL            COMMENT '1采纳 0拒绝',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ai_session_tenant_user` (`tenant_id`, `user_id`),
    KEY `idx_ai_session_design` (`tenant_id`, `design_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI Copilot 会话';

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
