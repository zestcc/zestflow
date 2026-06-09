-- 2026-06-08：Copilot 成熟化 — 会话待采纳快照、消息正文扩容（幂等：可重复执行）

SET @zf_db = DATABASE();

SET @zf_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @zf_db AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'pending_chain_data');
SET @zf_sql = IF(@zf_cnt = 0,
    'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `pending_chain_data` MEDIUMTEXT DEFAULT NULL COMMENT ''待采纳链 JSON'' AFTER `error_message`',
    'SELECT 1');
PREPARE zf_stmt FROM @zf_sql;
EXECUTE zf_stmt;
DEALLOCATE PREPARE zf_stmt;

SET @zf_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @zf_db AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'pending_summary');
SET @zf_sql = IF(@zf_cnt = 0,
    'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `pending_summary` VARCHAR(2000) DEFAULT NULL COMMENT ''待采纳摘要'' AFTER `pending_chain_data`',
    'SELECT 1');
PREPARE zf_stmt FROM @zf_sql;
EXECUTE zf_stmt;
DEALLOCATE PREPARE zf_stmt;

SET @zf_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @zf_db AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'pending_validation_json');
SET @zf_sql = IF(@zf_cnt = 0,
    'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `pending_validation_json` TEXT DEFAULT NULL COMMENT ''待采纳校验结果 JSON'' AFTER `pending_summary`',
    'SELECT 1');
PREPARE zf_stmt FROM @zf_sql;
EXECUTE zf_stmt;
DEALLOCATE PREPARE zf_stmt;

SET @zf_cnt = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @zf_db AND TABLE_NAME = 'zf_ai_copilot_session' AND COLUMN_NAME = 'last_model');
SET @zf_sql = IF(@zf_cnt = 0,
    'ALTER TABLE `zf_ai_copilot_session` ADD COLUMN `last_model` VARCHAR(128) DEFAULT NULL COMMENT ''最近调用模型'' AFTER `pending_validation_json`',
    'SELECT 1');
PREPARE zf_stmt FROM @zf_sql;
EXECUTE zf_stmt;
DEALLOCATE PREPARE zf_stmt;

ALTER TABLE `zf_ai_copilot_message`
    MODIFY COLUMN `content_summary` MEDIUMTEXT DEFAULT NULL COMMENT '消息正文（用户/助手）';
