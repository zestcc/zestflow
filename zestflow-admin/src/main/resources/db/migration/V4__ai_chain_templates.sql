-- Flyway V4 — AI 链模板库
-- 2026-06-02

CREATE TABLE IF NOT EXISTS `zf_ai_chain_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`       BIGINT       NOT NULL                COMMENT '租户ID',
    `name`            VARCHAR(128) NOT NULL                COMMENT '模板名称',
    `description`     VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `app_code`        VARCHAR(50)  DEFAULT NULL            COMMENT '应用编码',
    `prompt_summary`  VARCHAR(500) DEFAULT NULL            COMMENT '原始需求摘要',
    `chain_data`      MEDIUMTEXT   NOT NULL                COMMENT '链定义 JSON',
    `tags`            VARCHAR(256) DEFAULT NULL            COMMENT '标签 JSON 数组',
    `created_by`      VARCHAR(64)  DEFAULT NULL,
    `updated_by`      VARCHAR(64)  DEFAULT NULL,
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`      TINYINT      DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_ai_tpl_tenant_app` (`tenant_id`, `app_code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 链模板库';

-- 演示种子（租户 1，可按需删除）
INSERT INTO `zf_ai_chain_template` (`tenant_id`, `name`, `description`, `app_code`, `prompt_summary`, `chain_data`, `created_by`, `is_deleted`)
SELECT 1, '线性校验链骨架', 'START → 单任务 → END', 'demo-app', '最简单的线性链',
       '{"nodes":[{"id":"start","type":"START"},{"id":"task1","type":"TASK","componentId":"demoTask"},{"id":"end","type":"END"}],"edges":[{"source":"start","target":"task1"},{"source":"task1","target":"end"}]}',
       'system', 0
WHERE NOT EXISTS (SELECT 1 FROM `zf_ai_chain_template` WHERE `tenant_id` = 1 AND `name` = '线性校验链骨架' AND `is_deleted` = 0);
