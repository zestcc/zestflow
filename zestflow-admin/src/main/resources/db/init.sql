-- ZestFlow Admin 数据库初始化脚本
-- 使用前请先创建数据库: CREATE DATABASE IF NOT EXISTS zestflow DEFAULT CHARSET utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `username`      VARCHAR(50)  NOT NULL                 COMMENT '用户名',
    `email`         VARCHAR(100) DEFAULT NULL             COMMENT '邮箱',
    `password`      VARCHAR(255) NOT NULL                 COMMENT '密码（BCrypt）',
    `avatar`        VARCHAR(500) DEFAULT NULL             COMMENT '头像URL',
    `status`        TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-禁用',
    `is_super_admin` TINYINT     DEFAULT 0                COMMENT '是否超级管理员：1-是 0-否',
    `reset_token`   VARCHAR(64)  DEFAULT NULL             COMMENT '密码重置Token',
    `reset_token_expiry` DATETIME DEFAULT NULL            COMMENT '重置Token过期时间',
    `created_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 模块表
CREATE TABLE IF NOT EXISTS `module` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `code`        VARCHAR(50)  NOT NULL                 COMMENT '模块编码',
    `name`        VARCHAR(100) NOT NULL                 COMMENT '模块名称',
    `description` VARCHAR(500) DEFAULT NULL             COMMENT '描述',
    `status`      TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-禁用',
    `owner`       VARCHAR(50)  DEFAULT NULL             COMMENT '负责人',
    `sort_order`  INT          DEFAULT 0                COMMENT '排序序号',
    `retry_count` INT          DEFAULT 5                COMMENT '异常重试次数',
    `retry_interval` INT       DEFAULT 60               COMMENT '重试间隔（分钟）',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块表';

-- 角色表（预定义，不通过管理界面 CRUD）
CREATE TABLE IF NOT EXISTS `role` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `code`        VARCHAR(30)  NOT NULL                 COMMENT '角色编码：MODULE_ADMIN/MODULE_EDITOR/MODULE_VIEWER',
    `name`        VARCHAR(50)  NOT NULL                 COMMENT '角色名称',
    `description` VARCHAR(200) DEFAULT NULL             COMMENT '角色描述',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

INSERT INTO `role` (`code`, `name`, `description`) VALUES
('MODULE_ADMIN', '模块管理员', '可管理模块配置，可对模块下的链进行增删改查'),
('MODULE_EDITOR', '模块编辑', '可查看模块配置，可对模块下的链进行增删改查'),
('MODULE_VIEWER', '模块只读', '仅可查看模块配置和模块下的链');

-- 用户-模块-角色关联表
CREATE TABLE IF NOT EXISTS `user_module_role` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `user_id`    BIGINT   NOT NULL                 COMMENT '用户ID',
    `module_id`  BIGINT   NOT NULL                 COMMENT '模块ID',
    `role_id`    BIGINT   NOT NULL                 COMMENT '角色ID',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_module` (`user_id`, `module_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_module_id` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户模块角色关联表';

-- 执行器注册表
CREATE TABLE IF NOT EXISTS `executor_registry` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `module_id`       BIGINT       NOT NULL                 COMMENT '所属模块ID',
    `executor_id`     VARCHAR(100) NOT NULL                 COMMENT '执行器唯一标识',
    `executor_host`   VARCHAR(255) NOT NULL                 COMMENT '执行器Host',
    `executor_port`   INT          NOT NULL                 COMMENT '执行器Port',
    `status`          TINYINT      DEFAULT 1                COMMENT '状态：1-在线 0-离线',
    `last_heartbeat`  DATETIME     DEFAULT NULL             COMMENT '最后心跳时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_executor_id` (`executor_id`),
    KEY `idx_module_id` (`module_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器注册表';
