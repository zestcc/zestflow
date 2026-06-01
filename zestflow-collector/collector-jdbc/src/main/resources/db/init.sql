-- ZestFlow Collector 数据库初始化脚本 — DDL 集中管理
-- 未发布前可直接删表重来

-- ==================== 创建数据库 ====================

CREATE DATABASE IF NOT EXISTS `zestflow_app_log` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `zestflow_app_log`;

-- ==================== 日志表 ====================

CREATE TABLE IF NOT EXISTS `chain_event` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '自增主键',
    `event_id`      VARCHAR(64)  NOT NULL                 COMMENT '事件全局唯一 ID（UUID）',
    `event_type`    VARCHAR(32)  NOT NULL                 COMMENT '事件类型',
    `execution_id`  VARCHAR(64)  DEFAULT NULL             COMMENT '执行追踪 ID（同一次链执行的所有事件共享）',
    `chain_id`      VARCHAR(64)  DEFAULT NULL             COMMENT '链编码',
    `chain_name`    VARCHAR(128) DEFAULT NULL             COMMENT '链名称',
    `node_id`       VARCHAR(64)  DEFAULT NULL             COMMENT '节点编码',
    `node_name`     VARCHAR(128) DEFAULT NULL             COMMENT '节点名称',
    `executor_id`   VARCHAR(128) DEFAULT NULL             COMMENT '执行器 ID',
    `app_code`      VARCHAR(64)  DEFAULT NULL             COMMENT '应用编码',
    `app_name`      VARCHAR(64)  DEFAULT NULL             COMMENT '应用名',
    `tenant_id`     BIGINT       DEFAULT 1                COMMENT '租户ID',
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
    KEY `idx_app_event` (`app_name`, `event_type`),
    KEY `idx_execution_id` (`execution_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链执行事件表';

-- 2026-06-01：链图数据快照表，发布时快照供历史日志 X6 图还原
CREATE TABLE IF NOT EXISTS `chain_graph_snapshot` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '自增主键',
    `chain_code`  VARCHAR(64)  NOT NULL                            COMMENT '链编码',
    `version`     INT          NOT NULL                            COMMENT '版本号',
    `graph_data`  MEDIUMTEXT   DEFAULT NULL                        COMMENT '图数据 JSON',
    `status`      TINYINT      NOT NULL DEFAULT 1                  COMMENT '状态：1-生效 0-已废弃',
    `tenant_id`   BIGINT       DEFAULT 1                           COMMENT '租户ID',
    `app_code`    VARCHAR(50)  DEFAULT NULL                        COMMENT '应用编码',
    `created_by`  VARCHAR(64)  DEFAULT NULL                        COMMENT '创建人',
    `updated_by`  VARCHAR(64)  DEFAULT NULL                        COMMENT '最后更新人',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted`  TINYINT      DEFAULT 0                           COMMENT '删除标记（0-未删）',
    UNIQUE KEY `uk_chain_version` (`chain_code`, `version`),
    KEY `idx_lookup` (`chain_code`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链图数据快照表';
