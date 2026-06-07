-- Flyway V1 — Admin 库全量表结构（幂等：表已存在则跳过）
-- 2026-06-06 Beta 整合：含 AI Copilot、调度平台任务、SLA 告警、Token 月配额等原 V2–V7 变更
-- 2026-06-06 整合：字典级联/树形（parent_id、parent_type_code、parent_value、extra）+ sys_config
-- 增量 DDL 请新增 V2__*.sql

-- ==================== Admin 表 ====================

-- 2026-06-01：租户表 — 多租户基础
CREATE TABLE IF NOT EXISTS `tenant` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `name`           VARCHAR(128) NOT NULL                 COMMENT '租户名称',
    `code`           VARCHAR(64)  NOT NULL                 COMMENT '租户编码',
    `description`    VARCHAR(500) DEFAULT NULL             COMMENT '租户描述',
    `status`         TINYINT      DEFAULT 1                COMMENT '状态：1-正常 0-禁用',
    `tenant_type`    VARCHAR(16)  NOT NULL DEFAULT 'standard' COMMENT '租户类型：standard / trial',
    `provision_source` VARCHAR(16) DEFAULT NULL           COMMENT '开户来源：admin / api / ip',
    `expires_at`     DATETIME     DEFAULT NULL             COMMENT '到期时间（试玩）',
    `last_active_at` DATETIME     DEFAULT NULL             COMMENT '最后活跃时间',
    `created_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_trial_last_active` (`tenant_type`, `last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户表';

-- 2026-06-01：用户-租户关联表
CREATE TABLE IF NOT EXISTS `user_tenant` (
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
CREATE TABLE IF NOT EXISTS `tenant_ip_mapping` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `ip_address`      VARCHAR(64)  NOT NULL                 COMMENT 'IP地址',
    `tenant_id`       BIGINT       NOT NULL                 COMMENT '租户ID',
    `last_active_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ip_address` (`ip_address`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_last_active` (`last_active_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP-租户映射表（演示环境）';

CREATE TABLE IF NOT EXISTS `user` (
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

CREATE TABLE IF NOT EXISTS `role` (
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
    UNIQUE KEY `uk_tenant_role` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS `user_app_role` (
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
    UNIQUE KEY `uk_tenant_user_app` (`tenant_id`, `user_id`, `app_code`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户应用角色关联表';

CREATE TABLE IF NOT EXISTS `executor_registry` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `executor_id`    VARCHAR(100) NOT NULL                 COMMENT '执行器唯一标识',
    `app_code`       VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码（分组标识）',
    `app_name`       VARCHAR(100) DEFAULT NULL             COMMENT '应用名称',
    `executor_host`  VARCHAR(255) NOT NULL                 COMMENT '执行器Host',
    `executor_port`  INT          NOT NULL                 COMMENT '执行器Port',
    `status`         TINYINT      DEFAULT 1                COMMENT '状态：1-在线 0-离线 2-异常离线',
    `last_heartbeat` DATETIME     DEFAULT NULL             COMMENT '最后心跳时间',
    `declared_chain_keys` TEXT     DEFAULT NULL             COMMENT '@ZestChain 声明的 chain_key 列表 JSON',
    `tenant_id`      BIGINT       DEFAULT 1                COMMENT '租户ID',
    `created_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`     VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `updated_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_executor` (`tenant_id`, `executor_id`),
    KEY `idx_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器注册表';

CREATE TABLE IF NOT EXISTS `schedule` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `chain_id`          BIGINT       DEFAULT NULL             COMMENT '关联链ID（平台任务可为空）',
    `chain_code`        VARCHAR(128) DEFAULT NULL             COMMENT '链编码或平台任务键',
    `chain_name`        VARCHAR(128) DEFAULT NULL             COMMENT '链名称（冗余）',
    `job_type`          VARCHAR(16)  NOT NULL DEFAULT 'CHAIN' COMMENT 'CHAIN-业务链 PLATFORM-平台任务',
    `job_key`           VARCHAR(64)  DEFAULT NULL             COMMENT '平台任务唯一键',
    `cron`              VARCHAR(64)  NOT NULL                 COMMENT 'cron 表达式',
    `schedule_kind`     VARCHAR(16)  NOT NULL DEFAULT 'CRON'  COMMENT 'CRON|FIXED_RATE|FIXED_DELAY',
    `fixed_interval_ms` BIGINT       DEFAULT NULL             COMMENT '固定间隔毫秒（非 CRON 时使用）',
    `module`            VARCHAR(32)  DEFAULT NULL             COMMENT '所属模块 admin|executor|collector',
    `editable`          TINYINT      NOT NULL DEFAULT 1       COMMENT '是否允许编辑/删除',
    `remote`            TINYINT      NOT NULL DEFAULT 0       COMMENT '是否节点本地执行（仅元数据展示）',
    `last_trigger_at`   DATETIME     DEFAULT NULL             COMMENT '最近触发时间',
    `route_strategy`    VARCHAR(32)  DEFAULT 'round_robin'    COMMENT '路由策略：round_robin/hash/random',
    `params`            TEXT         DEFAULT NULL             COMMENT '执行参数 JSON',
    `status`            TINYINT      DEFAULT 1                COMMENT '状态：0-停用 1-启用',
    `remark`            VARCHAR(256) DEFAULT NULL             COMMENT '备注',
    `tenant_id`         BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`          VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码',
    `updated_by`        VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_by`        VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_key` (`job_key`),
    KEY `idx_chain_id` (`chain_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度定义表';

CREATE TABLE IF NOT EXISTS `schedule_log` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `schedule_id`      BIGINT       NOT NULL                 COMMENT '调度ID',
    `job_key`          VARCHAR(64)  DEFAULT NULL             COMMENT '平台任务键',
    `job_name`         VARCHAR(128) DEFAULT NULL             COMMENT '任务名称',
    `chain_code`       VARCHAR(128) DEFAULT NULL             COMMENT '链编码或平台任务键',
    `executor_id`      VARCHAR(128) DEFAULT NULL             COMMENT '选中的执行器ID',
    `executor_address` VARCHAR(256) DEFAULT NULL             COMMENT '执行器地址 host:port',
    `execution_id`     VARCHAR(64)  DEFAULT NULL             COMMENT '链执行追踪 ID（CHAIN 调度触发后回写）',
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
    KEY `idx_status` (`status`),
    KEY `idx_schedule_log_execution_id` (`execution_id`),
    KEY `idx_job_key` (`job_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度执行日志表';

CREATE TABLE IF NOT EXISTS `component` (
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
    UNIQUE KEY `uk_tenant_executor_component` (`tenant_id`, `executor_id`, `component_id`),
    KEY `idx_executor_id` (`executor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行元件注册表';

CREATE TABLE IF NOT EXISTS `collector_registry` (
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
    UNIQUE KEY `uk_tenant_collector` (`tenant_id`, `collector_id`),
    KEY `idx_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采集器注册表';

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
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
    UNIQUE KEY `uk_tenant_dict_type` (`tenant_id`, `code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `type_code`        VARCHAR(64)  NOT NULL                 COMMENT '字典类型编码',
    `parent_id`        BIGINT       DEFAULT NULL             COMMENT '父级数据项ID（同类型树）',
    `parent_type_code` VARCHAR(64)  DEFAULT NULL             COMMENT '父级字典类型（空=同类型）',
    `parent_value`     VARCHAR(128) DEFAULT NULL             COMMENT '父级字典项 value',
    `label`            VARCHAR(128) NOT NULL                 COMMENT '数据标签',
    `value`            VARCHAR(128) NOT NULL                 COMMENT '数据值',
    `sort`             INT          DEFAULT 0                COMMENT '排序号',
    `status`           TINYINT      DEFAULT 1                COMMENT '状态：0-停用 1-启用',
    `tag_type`         VARCHAR(32)  DEFAULT NULL             COMMENT '标签类型（primary/success/warning/danger/info）',
    `default_flag`     TINYINT      DEFAULT 0                COMMENT '是否默认：1-是 0-否',
    `remark`           VARCHAR(256) DEFAULT NULL             COMMENT '备注',
    `extra`            TEXT         DEFAULT NULL             COMMENT '扩展 JSON',
    `tenant_id`        BIGINT       DEFAULT 1                COMMENT '租户ID',
    `app_code`         VARCHAR(50)  DEFAULT NULL             COMMENT '应用编码（NULL=系统级）',
    `created_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '创建人',
    `updated_by`       VARCHAR(64)  DEFAULT NULL             COMMENT '最后修改人',
    `created_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_dict_data` (`tenant_id`, `type_code`, `value`),
    KEY `idx_type_code` (`type_code`),
    KEY `idx_dict_parent` (`tenant_id`, `type_code`, `parent_type_code`, `parent_value`),
    KEY `idx_dict_parent_id` (`tenant_id`, `type_code`, `parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

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

-- 2026-05-31：演示场景定义表 — 替代 application.yml 配置，DB 驱动 CRUD
CREATE TABLE IF NOT EXISTS `playground_scene` (
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
    UNIQUE KEY `uk_tenant_scene` (`tenant_id`, `scene_code`),
    KEY `idx_chain_code` (`chain_code`),
    KEY `idx_app_code` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试验场场景定义表';

-- 2026-06-01：演示执行记录表
CREATE TABLE IF NOT EXISTS `playground_record` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `scene_id`         BIGINT       DEFAULT NULL             COMMENT '关联场景ID',
    `scene_name`       VARCHAR(128) DEFAULT NULL             COMMENT '场景名称（冗余）',
    `scene_code`       VARCHAR(64)  DEFAULT NULL             COMMENT '场景编码（冗余）',
    `request_method`   VARCHAR(10)  NOT NULL DEFAULT 'POST'  COMMENT '请求方法',
    `request_path`     VARCHAR(256) NOT NULL                 COMMENT '请求路径',
    `request_headers`  TEXT         DEFAULT NULL             COMMENT '请求头 JSON',
    `body_type`        VARCHAR(10)  DEFAULT NULL             COMMENT '请求体类型',
    `invocation_id`    VARCHAR(64)  DEFAULT NULL             COMMENT '调用载荷 ID（request/response 存 app_log）',
    `response_status`  INT          DEFAULT NULL             COMMENT 'HTTP 响应状态码',
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
    KEY `idx_chain_code` (`chain_code`),
    KEY `idx_invocation_id` (`invocation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='试验场执行记录表';

-- ==================== AI Copilot ====================

CREATE TABLE IF NOT EXISTS `zf_ai_tenant_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`        BIGINT       NOT NULL                COMMENT '租户ID',
    `enabled`          TINYINT      DEFAULT 0               COMMENT '是否启用 Copilot',
    `preset`           VARCHAR(50)  DEFAULT 'deepseek'      COMMENT '提供商预设 ID',
    `base_url`         VARCHAR(512) DEFAULT NULL            COMMENT '覆盖 baseUrl',
    `api_key_enc`      VARCHAR(1024) DEFAULT NULL           COMMENT '加密 API Key',
    `model`            VARCHAR(100) DEFAULT NULL            COMMENT '覆盖模型名',
    `allowed_presets`      VARCHAR(512) DEFAULT NULL            COMMENT '允许的预设 JSON 数组',
    `monthly_token_quota`  INT          DEFAULT NULL            COMMENT '月 Token 估算上限，NULL=不限',
    `created_by`           VARCHAR(64)  DEFAULT NULL,
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
    `latency_ms`    INT          DEFAULT NULL            COMMENT 'LLM 调用耗时 ms',
    `success`       TINYINT      DEFAULT 1               COMMENT '1成功 0失败',
    `error_message` VARCHAR(500) DEFAULT NULL            COMMENT '失败摘要',
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

-- 演示种子（租户 1，可按需删除）
INSERT INTO `zf_ai_chain_template` (`tenant_id`, `name`, `description`, `app_code`, `prompt_summary`, `chain_data`, `created_by`, `is_deleted`)
SELECT 1, '线性校验链骨架', 'START → 单任务 → END', 'demo-app', '最简单的线性链',
       '{"nodes":[{"id":"start","type":"START"},{"id":"task1","type":"TASK","componentId":"demoTask"},{"id":"end","type":"END"}],"edges":[{"source":"start","target":"task1"},{"source":"task1","target":"end"}]}',
       'system', 0
WHERE NOT EXISTS (SELECT 1 FROM `zf_ai_chain_template` WHERE `tenant_id` = 1 AND `name` = '线性校验链骨架' AND `is_deleted` = 0);

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
