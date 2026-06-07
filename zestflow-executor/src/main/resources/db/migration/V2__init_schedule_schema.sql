-- Flyway V2 — 业务链调度（真源在业务库，Executor 自治 Cron）

CREATE TABLE IF NOT EXISTS `zf_schedule` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `chain_code`      VARCHAR(128) NOT NULL                COMMENT '链编码',
    `chain_name`      VARCHAR(128) NOT NULL DEFAULT ''     COMMENT '链名称',
    `cron`            VARCHAR(64)  NOT NULL                 COMMENT 'Cron 表达式',
    `schedule_kind`   VARCHAR(16)  NOT NULL DEFAULT 'CRON' COMMENT 'CRON|FIXED_RATE|FIXED_DELAY',
    `route_strategy`  VARCHAR(32)  NOT NULL DEFAULT 'local' COMMENT 'local|round_robin|hash',
    `shard_total`     INT          NOT NULL DEFAULT 1       COMMENT '分片总数',
    `shard_param`     VARCHAR(64)  DEFAULT NULL             COMMENT '分片哈希键，默认 schedule_id',
    `misfire_policy`  VARCHAR(16)  NOT NULL DEFAULT 'IGNORE' COMMENT 'IGNORE|FIRE_ONCE',
    `params`          TEXT         DEFAULT NULL             COMMENT '执行参数 JSON',
    `status`          TINYINT      NOT NULL DEFAULT 1       COMMENT '0-停用 1-启用',
    `remark`          VARCHAR(256) DEFAULT NULL             COMMENT '备注',
    `tenant_id`       BIGINT       NOT NULL DEFAULT 1       COMMENT '租户ID',
    `app_code`        VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `created_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`      VARCHAR(32)  DEFAULT NULL             COMMENT '创建时间',
    `updated_at`      VARCHAR(32)  DEFAULT NULL             COMMENT '更新时间',
    KEY `idx_status` (`status`),
    KEY `idx_app` (`tenant_id`, `app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务链调度定义（Executor 自治）';

CREATE TABLE IF NOT EXISTS `zf_schedule_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `schedule_id`      BIGINT       NOT NULL                 COMMENT '调度ID',
    `chain_code`       VARCHAR(128) NOT NULL                 COMMENT '链编码',
    `executor_id`      VARCHAR(128) DEFAULT NULL             COMMENT '执行器ID',
    `execution_id`     VARCHAR(64)  DEFAULT NULL             COMMENT '链执行追踪ID',
    `route_strategy`   VARCHAR(32)  DEFAULT NULL             COMMENT '路由策略',
    `trigger_type`     VARCHAR(32)  NOT NULL DEFAULT 'cron'  COMMENT 'cron|manual',
    `params`           TEXT         DEFAULT NULL             COMMENT '参数 JSON',
    `status`           TINYINT      NOT NULL DEFAULT 0       COMMENT '0-运行中 1-成功 2-失败',
    `error_message`    TEXT         DEFAULT NULL             COMMENT '错误信息',
    `cost_ms`          BIGINT       DEFAULT NULL             COMMENT '耗时毫秒',
    `triggered_at`     VARCHAR(32)  NOT NULL                 COMMENT '触发时间',
    `tenant_id`        BIGINT       NOT NULL DEFAULT 1       COMMENT '租户ID',
    `app_code`         VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    KEY `idx_schedule` (`schedule_id`),
    KEY `idx_triggered` (`triggered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务链调度执行日志';
