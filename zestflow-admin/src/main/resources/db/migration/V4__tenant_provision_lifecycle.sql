-- 2026-06-04：统一租户开户 — 类型 / 来源 / 到期时间（试玩与正式共用 Provision 入口）
-- 已有库手动执行（Flyway 默认关闭）；新库请直接用 init.sql

USE `zestflow_admin`;

ALTER TABLE `tenant`
    ADD COLUMN `tenant_type` VARCHAR(16) NOT NULL DEFAULT 'standard'
        COMMENT '租户类型：standard / trial' AFTER `status`,
    ADD COLUMN `provision_source` VARCHAR(16) DEFAULT NULL
        COMMENT '开户来源：admin / api / ip' AFTER `tenant_type`,
    ADD COLUMN `expires_at` DATETIME DEFAULT NULL
        COMMENT '到期时间（试玩可选硬上限；回收以 last_active_at 滑动窗口为主）' AFTER `provision_source`,
    ADD KEY `idx_trial_last_active` (`tenant_type`, `last_active_at`);

-- 历史 IP 试玩租户（code 以 demo- 开头）标记为 trial
UPDATE `tenant`
SET `tenant_type` = 'trial',
    `provision_source` = COALESCE(`provision_source`, 'ip')
WHERE `code` LIKE 'demo-%' AND `id` > 1;
