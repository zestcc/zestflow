-- ZestFlow Executor 数据库初始化脚本 — DDL 集中管理
-- 未发布前可直接删表重来

-- ==================== 创建数据库 ====================

-- CREATE DATABASE IF NOT EXISTS `zestflow_app_bussiness` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- USE `zestflow_app_bussiness`;

-- ==================== 业务表 ====================

-- 2026-05-31：移除 design_code 列，改由 zf_design_binding 表维护设计↔链关系
-- 2026-06-05：新增 chain_key — 应用侧稳定链标识
CREATE TABLE IF NOT EXISTS `zf_chain` (
    `code`        VARCHAR(64)  NOT NULL PRIMARY KEY,
    `chain_key`   VARCHAR(128) DEFAULT NULL         COMMENT '应用侧稳定链标识',
    `name`        VARCHAR(128) NOT NULL DEFAULT '',
    `description` VARCHAR(500) DEFAULT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1   COMMENT '0-停用 1-未设计 2-未发布 3-发布中 4-已发布',
    `version`     INT          NOT NULL DEFAULT 1   COMMENT '当前发布版本号',
    `created_by`  VARCHAR(64)  DEFAULT NULL         COMMENT '创建人',
    `updated_by`  VARCHAR(64)  DEFAULT NULL         COMMENT '最后修改人',
    `tenant_id`   BIGINT       DEFAULT 1            COMMENT '租户ID',
    `app_code`    VARCHAR(50)  DEFAULT NULL         COMMENT '应用编码',
    `is_deleted`  TINYINT      DEFAULT 0            COMMENT '删除标记（0-未删 1-已删）',
    `created_at`  VARCHAR(32)  DEFAULT NULL         COMMENT '创建时间',
    `updated_at`  VARCHAR(32)  DEFAULT NULL         COMMENT '更新时间',
    UNIQUE KEY `uk_app_chain_key` (`tenant_id`, `app_code`, `chain_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Executor 链定义';

CREATE TABLE IF NOT EXISTS `zf_design` (
    `code`        VARCHAR(64)  NOT NULL PRIMARY KEY,
    `name`        VARCHAR(128) NOT NULL DEFAULT '',
    `description` VARCHAR(500) DEFAULT NULL,
    `designer`    VARCHAR(64)  DEFAULT NULL,
    `status`      TINYINT      NOT NULL DEFAULT 1  COMMENT '1-启用 0-停用',
    `graph_data`  TEXT         DEFAULT NULL,
    `chain_data`  TEXT         DEFAULT NULL        COMMENT '翻译后的链定义 JSON（ChainDefinitionDTO 格式）',
    `created_by`  VARCHAR(64)  DEFAULT NULL        COMMENT '创建人',
    `updated_by`  VARCHAR(64)  DEFAULT NULL        COMMENT '最后修改人',
    `tenant_id`   BIGINT       DEFAULT 1           COMMENT '租户ID',
    `app_code`    VARCHAR(50)  DEFAULT NULL        COMMENT '应用编码',
    `is_deleted`  TINYINT      DEFAULT 0           COMMENT '删除标记（0-未删 1-已删）',
    `created_at`  VARCHAR(32)  DEFAULT NULL        COMMENT '创建时间',
    `updated_at`  VARCHAR(32)  DEFAULT NULL        COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Executor 设计定义';

CREATE TABLE IF NOT EXISTS `zf_design_binding` (
    `design_code` VARCHAR(64) NOT NULL,
    `chain_code`  VARCHAR(64) NOT NULL,
    `tenant_id`   BIGINT      DEFAULT 1            COMMENT '租户ID',
    `app_code`    VARCHAR(50) DEFAULT NULL         COMMENT '应用编码',
    PRIMARY KEY (`design_code`, `chain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设计与链绑定关系';

CREATE TABLE IF NOT EXISTS `zf_chain_version` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `chain_code`  VARCHAR(64)  NOT NULL                COMMENT '链编码',
    `version`     INT          NOT NULL                COMMENT '版本号',
    `design_code` VARCHAR(64)  DEFAULT NULL            COMMENT '关联设计编码',
    `graph_data`  MEDIUMTEXT   DEFAULT NULL            COMMENT '图数据 JSON 快照',
    `chain_data`  MEDIUMTEXT   DEFAULT NULL            COMMENT '链定义 JSON 快照',
    `created_by`  VARCHAR(64)  DEFAULT NULL            COMMENT '创建人',
    `tenant_id`   BIGINT       DEFAULT 1               COMMENT '租户ID',
    `app_code`    VARCHAR(50)  DEFAULT NULL            COMMENT '应用编码',
    `created_at`  VARCHAR(32)  NOT NULL                COMMENT '创建时间',
    INDEX `idx_chain_version` (`chain_code`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='链版本快照表';

-- ==================== 调度表（与 Flyway V2 一致） ====================

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
