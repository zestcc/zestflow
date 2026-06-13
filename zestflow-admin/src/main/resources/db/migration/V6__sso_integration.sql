-- 2026-06-13：SSO 用户联邦字段（sso_subject / sso_provider）— 幂等，兼容列已存在场景
SET @db = DATABASE();

SET @cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND COLUMN_NAME = 'sso_subject');
SET @sql = IF(@cnt = 0,
    'ALTER TABLE `user` ADD COLUMN `sso_subject` VARCHAR(128) DEFAULT NULL COMMENT ''SSO 用户唯一标识(sub)'' AFTER `email`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND COLUMN_NAME = 'sso_provider');
SET @sql = IF(@cnt = 0,
    'ALTER TABLE `user` ADD COLUMN `sso_provider` VARCHAR(32) DEFAULT NULL COMMENT ''SSO 提供方'' AFTER `sso_subject`',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @cnt = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'user' AND INDEX_NAME = 'uk_sso_provider_subject');
SET @sql = IF(@cnt = 0,
    'ALTER TABLE `user` ADD UNIQUE KEY `uk_sso_provider_subject` (`sso_provider`, `sso_subject`)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
