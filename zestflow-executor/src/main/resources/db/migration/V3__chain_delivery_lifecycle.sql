-- 2026-06-08：链交付生命周期（bootstrap 占位 vs production 生产链）
ALTER TABLE `zf_chain`
    ADD COLUMN `delivery_lifecycle` VARCHAR(16) NOT NULL DEFAULT 'bootstrap'
        COMMENT '交付生命周期：bootstrap-占位 production-生产'
        AFTER `version`;
