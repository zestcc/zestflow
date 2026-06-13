-- 2026-06-13：SSO 用户联邦字段（sso_subject / sso_provider）
ALTER TABLE `user`
    ADD COLUMN `sso_subject` VARCHAR(128) DEFAULT NULL COMMENT 'SSO 用户唯一标识(sub)' AFTER `email`,
    ADD COLUMN `sso_provider` VARCHAR(32) DEFAULT NULL COMMENT 'SSO 提供方' AFTER `sso_subject`;

ALTER TABLE `user`
    ADD UNIQUE KEY `uk_sso_provider_subject` (`sso_provider`, `sso_subject`);
