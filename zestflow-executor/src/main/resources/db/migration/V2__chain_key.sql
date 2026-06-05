-- 2026-06-05：Executor 业务库 — 链稳定标识 chain_key
ALTER TABLE `zf_chain`
    ADD COLUMN IF NOT EXISTS `chain_key` VARCHAR(128) DEFAULT NULL COMMENT '应用侧稳定链标识' AFTER `code`;

-- MySQL 8.0.12 不支持 IF NOT EXISTS on index — 重复执行请忽略 Duplicate key name
CREATE UNIQUE INDEX `uk_app_chain_key` ON `zf_chain` (`tenant_id`, `app_code`, `chain_key`);
