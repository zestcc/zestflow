-- Flyway V7 — Copilot 租户 Token 月配额（P6）
-- 2026-06-07

ALTER TABLE `zf_ai_tenant_config`
    ADD COLUMN `monthly_token_quota` INT DEFAULT NULL COMMENT '月 Token 估算上限，NULL=不限' AFTER `allowed_presets`;
