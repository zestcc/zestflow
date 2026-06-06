-- 2026-06-05：存量库补 declared_chain_keys（V1 新装已含该列，须幂等）
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'executor_registry'
      AND COLUMN_NAME = 'declared_chain_keys'
);
SET @ddl = IF(
    @col_exists = 0,
    'ALTER TABLE `executor_registry` ADD COLUMN `declared_chain_keys` TEXT DEFAULT NULL COMMENT ''@ZestChain 声明的 chain_key 列表 JSON'' AFTER `last_heartbeat`',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
