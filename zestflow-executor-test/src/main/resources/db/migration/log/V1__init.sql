-- ZestFlow 事件日志数据库初始化（V1）
-- 由 zestflow-executor-test 的日志数据源 Flyway 管理

CREATE TABLE `chain_event` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '自增主键',
    `event_id`      VARCHAR(64)  NOT NULL                 COMMENT '事件全局唯一 ID（UUID）',
    `event_type`    VARCHAR(32)  NOT NULL                 COMMENT '事件类型',
    `chain_id`      VARCHAR(64)  DEFAULT NULL             COMMENT '链实例 ID',
    `chain_name`    VARCHAR(128) DEFAULT NULL             COMMENT '链名称',
    `node_id`       VARCHAR(64)  DEFAULT NULL             COMMENT '节点实例 ID',
    `node_name`     VARCHAR(128) DEFAULT NULL             COMMENT '节点名称',
    `executor_id`   VARCHAR(128) DEFAULT NULL             COMMENT '执行器 ID',
    `app_name`      VARCHAR(64)  DEFAULT NULL             COMMENT '应用名',
    `params`        TEXT         DEFAULT NULL             COMMENT '执行入参 JSON',
    `result`        TEXT         DEFAULT NULL             COMMENT '执行结果 JSON',
    `error_message` TEXT         DEFAULT NULL             COMMENT '错误消息',
    `cost_ms`       BIGINT       DEFAULT NULL             COMMENT '执行耗时（毫秒）',
    `status`        TINYINT      DEFAULT NULL             COMMENT '节点状态：0-失败 1-成功',
    `timestamp`     BIGINT       NOT NULL                 COMMENT '事件发生时间戳（毫秒）',
    `metadata`      TEXT         DEFAULT NULL             COMMENT '扩展元数据 JSON',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_id` (`event_id`),
    KEY `idx_chain_id` (`chain_id`),
    KEY `idx_executor_id` (`executor_id`),
    KEY `idx_timestamp` (`timestamp`),
    KEY `idx_app_event` (`app_name`, `event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链执行事件表';
