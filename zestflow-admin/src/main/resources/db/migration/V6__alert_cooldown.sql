-- SLA 告警冷却记录 — 同一规则在冷却期内不重复发信
CREATE TABLE IF NOT EXISTS `alert_cooldown` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `alert_key`    VARCHAR(192) NOT NULL                COMMENT '告警键 tenant:app:rule',
    `last_sent_at` DATETIME     NOT NULL                COMMENT '上次发送时间',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_alert_key` (`alert_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SLA 告警冷却';
