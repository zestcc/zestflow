-- 2026-06-05：执行器注册表 — 存储 @ZestChain 声明的 chain_key 列表（JSON 数组）
ALTER TABLE `executor_registry`
    ADD COLUMN `declared_chain_keys` TEXT DEFAULT NULL COMMENT '@ZestChain 声明的 chain_key 列表 JSON' AFTER `last_heartbeat`;
