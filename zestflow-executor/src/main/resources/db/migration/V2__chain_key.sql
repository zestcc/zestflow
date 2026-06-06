-- 2026-06-05：存量库补 chain_key（V1 新装已含该列，须幂等）
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'zf_chain'
      AND COLUMN_NAME = 'chain_key'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE `zf_chain` ADD COLUMN `chain_key` VARCHAR(128) DEFAULT NULL COMMENT ''应用侧稳定链标识'' AFTER `code`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'zf_chain'
      AND INDEX_NAME = 'uk_app_chain_key'
);
SET @ddl = IF(
    @idx_exists = 0,
    'CREATE UNIQUE INDEX `uk_app_chain_key` ON `zf_chain` (`tenant_id`, `app_code`, `chain_key`)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
