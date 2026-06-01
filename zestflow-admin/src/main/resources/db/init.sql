-- ZestFlow 数据库初始化脚本
-- 所有 DDL 集中管理，未发布前可直接删表重来

-- ==================== 创建数据库 ====================

CREATE DATABASE IF NOT EXISTS `zestflow_admin` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `zestflow_test_bussiness` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `zestflow_test_log` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ==================== Admin 库（zestflow_admin） ====================

-- 2026-06-01：租户表 — 多租户基础
CREATE TABLE `zestflow_admin`.`tenant` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `name`           VARCHAR(128) NOT NULL                 COMMENT '租户名称',
    `code`           VARCHAR(64)  NOT NULL                 COMMENT '租户编码',
    `description`    VARCHAR(500) DEFAULT NULL             COMMENT '租户描述',
    `status`         TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-禁用',
    `last_active_at` DATETIME     DEFAULT NULL             COMMENT '最后活跃时间',
    `created_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- 2026-06-01：用户-租户关联表
CREATE TABLE `zestflow_admin`.`user_tenant` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `user_id`         BIGINT   NOT NULL                 COMMENT '用户ID',
    `tenant_id`       BIGINT   NOT NULL                 COMMENT '租户ID',
    `is_tenant_admin` TINYINT  DEFAULT 0                COMMENT '是否租户管理员：1-是 0-否',
    `created_by`      VARCHAR(64) DEFAULT NULL          COMMENT '创建人',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户租户关联表';

-- 2026-06-01：IP-租户映射表（演示环境自动映射）
CREATE TABLE `zestflow_admin`.`tenant_ip_mapping` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `ip_address`      VARCHAR(64)  NOT NULL                 COMMENT 'IP地址',
    `tenant_id`       BIGINT       NOT NULL                 COMMENT '租户ID',
    `last_active_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_ip` (`ip_address`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_last_active` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP-租户映射表（演示环境）';

CREATE TABLE `zestflow_admin`.`user` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `username`             VARCHAR(50)  NOT NULL                 COMMENT '用户名',
    `email`                VARCHAR(100) DEFAULT NULL             COMMENT '邮箱',
    `password`             VARCHAR(255) NOT NULL                 COMMENT '密码（BCrypt）',
    `avatar`               VARCHAR(500) DEFAULT NULL             COMMENT '头像URL',
    `status`               TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-禁用',
    `is_super_admin`       TINYINT      DEFAULT 0                COMMENT '是否超级管理员：1-是 0-否',
    `must_change_password` TINYINT      DEFAULT 0                COMMENT '需要强制修改密码：1-是 0-否',
    `tenant_id`            BIGINT       DEFAULT 1                COMMENT '租户ID',
    `reset_token`          VARCHAR(255) DEFAULT NULL             COMMENT '重置密码Token',
    `reset_token_expiry`   DATETIME     DEFAULT NULL             COMMENT 'Token过期时间',
    `email_verified`       TINYINT      DEFAULT 0                COMMENT '邮箱是否已验证：1-是 0-否',
    `verify_token`         VARCHAR(255) DEFAULT NULL             COMMENT '邮箱验证Token',
    `verify_token_expiry`  DATETIME     DEFAULT NULL             COMMENT '验证Token过期时间',
    `created_by`           VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`           VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`           DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE `zestflow_admin`.`role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `code`        VARCHAR(30)  NOT NULL                 COMMENT '角色编码：APP_ADMIN/APP_EDITOR/APP_VIEWER',
    `name`        VARCHAR(50)  NOT NULL                 COMMENT '角色名称',
    `description` VARCHAR(200) DEFAULT NULL             COMMENT '角色描述',
    `tenant_id`   BIGINT       DEFAULT 1                COMMENT '租户ID',
    `created_by`  VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`  VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

INSERT INTO `zestflow_admin`.`role` (`code`, `name`, `description`) VALUES
('APP_ADMIN', '应用管理员', '可管理应用配置，可对应用下的链进行增删改查'),
('APP_EDITOR', '应用编辑', '可查看应用配置，可对应用下的链进行增删改查'),
('APP_VIEWER', '应用只读', '仅可查看应用配置和应用下的链');

CREATE TABLE `zestflow_admin`.`user_app_role` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `user_id`    BIGINT   NOT NULL                 COMMENT '用户ID',
    `app_code`   VARCHAR(50) NOT NULL              COMMENT '应用编码',
    `role_id`    BIGINT   NOT NULL                 COMMENT '角色ID',
    `tenant_id`  BIGINT   DEFAULT 1                COMMENT '租户ID',
    `created_by` VARCHAR(64) DEFAULT NULL          COMMENT '创建人',
    `updated_by` VARCHAR(64) DEFAULT NULL          COMMENT '最后修改人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_app` (`user_id`, `app_code`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户应用角色关联表';

CREATE TABLE `zestflow_admin`.`executor_registry` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `executor_id`    VARCHAR(100) NOT NULL                 COMMENT '执行器唯一标识',
    `app_code`       VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码（分组标识）',
    `app_name`       VARCHAR(100) DEFAULT NULL             COMMENT '应用名称',
    `executor_host`  VARCHAR(255) NOT NULL                 COMMENT '执行器Host',
    `executor_port`  INT          NOT NULL                 COMMENT '执行器Port',
    `status`         TINYINT      DEFAULT 1                COMMENT '状态：1-在线 0-离线 2-异常离线',
    `last_heartbeat` DATETIME     DEFAULT NULL             COMMENT '最后心跳时间',
    `tenant_id`      BIGINT       DEFAULT 1                COMMENT '租户ID',
    `created_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_executor_id` (`executor_id`),
    KEY `idx_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器注册表';

CREATE TABLE `zestflow_admin`.`schedule` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `chain_id`       BIGINT       NOT NULL                 COMMENT '关联链ID',
    `chain_code`     VARCHAR(128) NOT NULL                 COMMENT '链编码（冗余，方便执行器执行）',
    `chain_name`     VARCHAR(128) DEFAULT NULL             COMMENT '链名称（冗余）',
    `cron`           VARCHAR(64)  NOT NULL                 COMMENT 'cron 表达式',
    `route_strategy` VARCHAR(32)  DEFAULT 'round_robin'    COMMENT '路由策略：round_robin/hash/random',
    `params`         TEXT         DEFAULT NULL             COMMENT '执行参数 JSON',
    `status`         TINYINT      DEFAULT 1                COMMENT '状态：0-停用 1-启用',
    `remark`         VARCHAR(256) DEFAULT NULL             COMMENT '备注',
    `tenant_id`      BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`       VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `updated_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_chain_id` (`chain_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度定义表';

CREATE TABLE `zestflow_admin`.`schedule_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `schedule_id`      BIGINT       NOT NULL                 COMMENT '调度ID',
    `chain_code`       VARCHAR(128) NOT NULL                 COMMENT '链编码',
    `executor_id`      VARCHAR(128) DEFAULT NULL             COMMENT '选中的执行器ID',
    `executor_address` VARCHAR(256) DEFAULT NULL             COMMENT '执行器地址 host:port',
    `route_strategy`   VARCHAR(32)  DEFAULT NULL             COMMENT '使用的路由策略',
    `trigger_type`     VARCHAR(32)  DEFAULT 'cron'           COMMENT '触发方式：cron/manual/api',
    `params`           TEXT         DEFAULT NULL             COMMENT '执行参数 JSON',
    `status`           TINYINT      DEFAULT 0                COMMENT '状态：0-运行中 1-成功 2-失败 3-超时',
    `result_data`      TEXT         DEFAULT NULL             COMMENT '执行结果 JSON',
    `error_message`    TEXT         DEFAULT NULL             COMMENT '错误信息',
    `cost_ms`          BIGINT       DEFAULT NULL             COMMENT '执行耗时（毫秒）',
    `triggered_at`     DATETIME     NOT NULL                 COMMENT '触发时间',
    `tenant_id`        BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`         VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `created_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_schedule_id` (`schedule_id`),
    KEY `idx_triggered_at` (`triggered_at`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度执行日志表';

CREATE TABLE `zestflow_admin`.`component` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `executor_id`     VARCHAR(128) NOT NULL                 COMMENT '执行器唯一标识',
    `component_id`    VARCHAR(128) NOT NULL                 COMMENT '元件 ID（@ZestExecute value 或 类名.方法名）',
    `component_name`  VARCHAR(128) DEFAULT NULL             COMMENT '元件显示名称',
    `description`     VARCHAR(500) DEFAULT NULL             COMMENT '元件描述',
    `group_name`      VARCHAR(100) DEFAULT NULL             COMMENT '分组名（@ZestComponent value）',
    `timeout`         BIGINT       DEFAULT -1               COMMENT '超时时间(ms)，-1 使用默认值',
    `is_async`        TINYINT      DEFAULT 0                COMMENT '是否异步：1-是 0-否',
    `status`          TINYINT      DEFAULT 1                COMMENT '状态：1-在线 0-离线（执行器下线）',
    `tenant_id`       BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`        VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `created_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`      VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次发现时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_executor_component` (`executor_id`, `component_id`),
    KEY `idx_executor_id` (`executor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行元件注册表';

-- ==================== 业务库（zestflow_test_bussiness） ====================

-- 2026-05-31：移除 design_code 列，改由 zf_design_binding 表维护设计↔链关系
CREATE TABLE `zestflow_test_bussiness`.`zf_chain` (
    `code`        VARCHAR(64)  NOT NULL PRIMARY KEY,
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
    `updated_at`  VARCHAR(32)  DEFAULT NULL         COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Executor 链定义';

CREATE TABLE `zestflow_test_bussiness`.`zf_design` (
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

CREATE TABLE `zestflow_test_bussiness`.`zf_design_binding` (
    `design_code` VARCHAR(64) NOT NULL,
    `chain_code`  VARCHAR(64) NOT NULL,
    `tenant_id`   BIGINT      DEFAULT 1            COMMENT '租户ID',
    `app_code`    VARCHAR(50) DEFAULT NULL         COMMENT '应用编码',
    PRIMARY KEY (`design_code`, `chain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设计与链绑定关系';

CREATE TABLE `zestflow_test_bussiness`.`zf_chain_version` (
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

CREATE TABLE `zestflow_admin`.`collector_registry` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `collector_id`   VARCHAR(128) NOT NULL                 COMMENT '采集器唯一标识',
    `app_code`       VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码（分组标识）',
    `app_name`       VARCHAR(100) DEFAULT NULL             COMMENT '应用名称',
    `collector_host` VARCHAR(255) NOT NULL                 COMMENT '采集器Host',
    `collector_port` INT          NOT NULL                 COMMENT '采集器Port',
    `status`         TINYINT      DEFAULT 1                COMMENT '状态：1-在线 0-离线 2-异常离线',
    `last_heartbeat` DATETIME     DEFAULT NULL             COMMENT '最后心跳时间',
    `tenant_id`      BIGINT       DEFAULT 1                COMMENT '租户ID',
    `created_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_collector_id` (`collector_id`),
    KEY `idx_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集器注册表';

CREATE TABLE `zestflow_admin`.`sys_dict_type` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `code`        VARCHAR(64)  NOT NULL                 COMMENT '字典编码（唯一）',
    `name`        VARCHAR(128) NOT NULL                 COMMENT '字典名称',
    `description` VARCHAR(256) DEFAULT NULL             COMMENT '字典描述',
    `status`      TINYINT      DEFAULT 1                COMMENT '状态：0-停用 1-启用',
    `sort`        INT          DEFAULT 0                COMMENT '排序号',
    `tenant_id`   BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`    VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码（NULL=系统级）',
    `created_by`  VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`  VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE `zestflow_admin`.`sys_dict_data` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `type_code`    VARCHAR(64)  NOT NULL                 COMMENT '字典类型编码',
    `label`        VARCHAR(128) NOT NULL                 COMMENT '数据标签',
    `value`        VARCHAR(128) NOT NULL                 COMMENT '数据值',
    `sort`         INT          DEFAULT 0                COMMENT '排序号',
    `status`       TINYINT      DEFAULT 1                COMMENT '状态：0-停用 1-启用',
    `tag_type`     VARCHAR(32)  DEFAULT NULL             COMMENT '标签类型（primary/success/warning/danger/info）',
    `default_flag` TINYINT      DEFAULT 0                COMMENT '是否默认：1-是 0-否',
    `remark`       VARCHAR(256) DEFAULT NULL             COMMENT '备注',
    `tenant_id`    BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`     VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码（NULL=系统级）',
    `created_by`   VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`   VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

-- 2026-05-31：演示场景定义表 — 替代 application.yml 配置，DB 驱动 CRUD
CREATE TABLE `zestflow_admin`.`playground_scene` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `scene_code`       VARCHAR(64)  NOT NULL                 COMMENT '场景编码（SCN{yMMdd}{6位序号}）',
    `name`             VARCHAR(128) NOT NULL                 COMMENT '场景名称',
    `description`      VARCHAR(500) DEFAULT NULL             COMMENT '场景描述',
    `request_path`     VARCHAR(256) NOT NULL                 COMMENT '请求路径',
    `request_method`   VARCHAR(10)  NOT NULL DEFAULT 'POST'  COMMENT '请求方法',
    `request_headers`  TEXT         DEFAULT NULL             COMMENT '默认请求头 JSON',
    `body_type`        VARCHAR(10)  NOT NULL DEFAULT 'JSON'  COMMENT '请求体类型',
    `request_body`     TEXT         DEFAULT NULL             COMMENT '请求体模板',
    `response_example` TEXT         DEFAULT NULL             COMMENT '响应示例 JSON',
    `chain_code`       VARCHAR(64)  NOT NULL                 COMMENT '关联链编码',
    `rate_limit`       INT          DEFAULT 30               COMMENT '每 IP 每分钟限流数',
    `tenant_id`        BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`         VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `created_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scene_code` (`scene_code`),
    KEY `idx_chain_code` (`chain_code`),
    KEY `idx_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试验场场景定义表';

-- 2026-06-01：演示执行记录表
CREATE TABLE `zestflow_admin`.`playground_record` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `scene_id`         BIGINT       DEFAULT NULL             COMMENT '关联场景ID',
    `scene_name`       VARCHAR(128) DEFAULT NULL             COMMENT '场景名称（冗余）',
    `scene_code`       VARCHAR(64)  DEFAULT NULL             COMMENT '场景编码（冗余）',
    `request_method`   VARCHAR(10)  NOT NULL DEFAULT 'POST'  COMMENT '请求方法',
    `request_path`     VARCHAR(256) NOT NULL                 COMMENT '请求路径',
    `request_headers`  TEXT         DEFAULT NULL             COMMENT '请求头 JSON',
    `body_type`        VARCHAR(10)  DEFAULT NULL             COMMENT '请求体类型',
    `request_body`     TEXT         DEFAULT NULL             COMMENT '请求体 JSON',
    `response_status`  INT          DEFAULT NULL             COMMENT 'HTTP 响应状态码',
    `response_body`    TEXT         DEFAULT NULL             COMMENT '响应体 JSON',
    `response_headers` TEXT         DEFAULT NULL             COMMENT '响应头 JSON',
    `chain_code`       VARCHAR(64)  DEFAULT NULL             COMMENT '关联链编码',
    `instance_id`      VARCHAR(128) DEFAULT NULL             COMMENT '链执行实例 ID',
    `status`           TINYINT      DEFAULT 0                COMMENT '执行状态：0-失败 1-成功',
    `cost_ms`          BIGINT       DEFAULT NULL             COMMENT '耗时（毫秒）',
    `error_msg`        VARCHAR(500) DEFAULT NULL             COMMENT '错误信息',
    `request_ip`       VARCHAR(64)  DEFAULT NULL             COMMENT '请求IP（仅入库，API 不返回）',
    `tenant_id`        BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`         VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `created_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_scene_id` (`scene_id`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_status` (`status`),
    KEY `idx_chain_code` (`chain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试验场执行记录表';

-- ==================== 日志库（zestflow_test_log） ====================

CREATE TABLE `zestflow_test_log`.`chain_event` (
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
CREATE TABLE IF NOT EXISTS `zestflow_test_log`.`chain_graph_snapshot` (
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
