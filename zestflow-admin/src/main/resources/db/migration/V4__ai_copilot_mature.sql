-- 2026-06-08：Copilot 成熟化 — 会话待采纳快照、消息正文扩容

ALTER TABLE `zf_ai_copilot_session`
    ADD COLUMN `pending_chain_data` MEDIUMTEXT DEFAULT NULL COMMENT '待采纳链 JSON' AFTER `error_message`,
    ADD COLUMN `pending_summary` VARCHAR(2000) DEFAULT NULL COMMENT '待采纳摘要' AFTER `pending_chain_data`,
    ADD COLUMN `pending_validation_json` TEXT DEFAULT NULL COMMENT '待采纳校验结果 JSON' AFTER `pending_summary`,
    ADD COLUMN `last_model` VARCHAR(128) DEFAULT NULL COMMENT '最近调用模型' AFTER `pending_validation_json`;

ALTER TABLE `zf_ai_copilot_message`
    MODIFY COLUMN `content_summary` MEDIUMTEXT DEFAULT NULL COMMENT '消息正文（用户/助手）';
