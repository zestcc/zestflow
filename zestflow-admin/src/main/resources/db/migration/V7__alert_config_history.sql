-- SLA 告警租户配置（阈值等可 UI 调整；收件人仍由 user_app_role 动态解析）
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

-- SLA 告警发送历史
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
