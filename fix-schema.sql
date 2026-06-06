-- 修复 zestflow_app_bussiness 数据库 schema
-- 添加缺失的 chain_key 列

USE `zestflow_app_bussiness`;

-- 检查并添加 chain_key 列
ALTER TABLE `zf_chain` ADD COLUMN IF NOT EXISTS `chain_key` VARCHAR(128) DEFAULT NULL COMMENT '应用侧稳定链标识' AFTER `code`;

-- 添加唯一索引（如果不存在）
-- ALTER TABLE `zf_chain` ADD UNIQUE INDEX IF NOT EXISTS `uk_app_chain_key` (`tenant_id`, `app_code`, `chain_key`);
