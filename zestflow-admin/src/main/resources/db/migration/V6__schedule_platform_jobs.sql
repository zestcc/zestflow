-- 调度中心扩展：业务链 Cron + 平台系统任务统一管理
ALTER TABLE `schedule`
    MODIFY COLUMN `chain_id` BIGINT NULL COMMENT '关联链ID（平台任务可为空）',
    MODIFY COLUMN `chain_code` VARCHAR(128) NULL COMMENT '链编码或平台任务键',
    ADD COLUMN `job_type` VARCHAR(16) NOT NULL DEFAULT 'CHAIN' COMMENT 'CHAIN-业务链 PLATFORM-平台任务' AFTER `chain_name`,
    ADD COLUMN `job_key` VARCHAR(64) NULL COMMENT '平台任务唯一键' AFTER `job_type`,
    ADD COLUMN `schedule_kind` VARCHAR(16) NOT NULL DEFAULT 'CRON' COMMENT 'CRON|FIXED_RATE|FIXED_DELAY' AFTER `cron`,
    ADD COLUMN `fixed_interval_ms` BIGINT NULL COMMENT '固定间隔毫秒（非 CRON 时使用）' AFTER `schedule_kind`,
    ADD COLUMN `module` VARCHAR(32) NULL COMMENT '所属模块 admin|executor|collector' AFTER `fixed_interval_ms`,
    ADD COLUMN `editable` TINYINT NOT NULL DEFAULT 1 COMMENT '是否允许编辑/删除' AFTER `module`,
    ADD COLUMN `remote` TINYINT NOT NULL DEFAULT 0 COMMENT '是否节点本地执行（仅元数据展示）' AFTER `editable`,
    ADD COLUMN `last_trigger_at` DATETIME NULL COMMENT '最近触发时间' AFTER `remote`,
    ADD UNIQUE KEY `uk_job_key` (`job_key`);

ALTER TABLE `schedule_log`
    MODIFY COLUMN `chain_code` VARCHAR(128) NULL COMMENT '链编码或平台任务键',
    ADD COLUMN `job_key` VARCHAR(64) NULL COMMENT '平台任务键' AFTER `schedule_id`,
    ADD COLUMN `job_name` VARCHAR(128) NULL COMMENT '任务名称' AFTER `job_key`,
    ADD KEY `idx_job_key` (`job_key`);
