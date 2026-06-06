-- Flyway V5 — 租户 RAG 文档 + Copilot 用量统计字段
-- 2026-06-02

CREATE TABLE IF NOT EXISTS `zf_ai_rag_document` (
    `id`           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `tenant_id`    BIGINT        NOT NULL                COMMENT '租户ID',
    `title`        VARCHAR(200)  NOT NULL                COMMENT '文档标题',
    `app_code`     VARCHAR(50)   DEFAULT NULL            COMMENT '可选应用范围，空=租户全局',
    `content`      MEDIUMTEXT    NOT NULL                COMMENT 'Markdown 正文',
    `enabled`      TINYINT       DEFAULT 1               COMMENT '1启用 0禁用',
    `sort_order`   INT           DEFAULT 0               COMMENT '排序',
    `source_type`  VARCHAR(16)   DEFAULT 'upload'        COMMENT 'upload|filesystem',
    `created_by`   VARCHAR(64)   DEFAULT NULL,
    `updated_by`   VARCHAR(64)   DEFAULT NULL,
    `created_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`   TINYINT       DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_ai_rag_doc_tenant` (`tenant_id`, `enabled`, `is_deleted`),
    KEY `idx_ai_rag_doc_app` (`tenant_id`, `app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户 AI RAG 文档';

ALTER TABLE `zf_ai_copilot_session`
    ADD COLUMN `latency_ms` INT DEFAULT NULL COMMENT 'LLM 调用耗时 ms' AFTER `adopted`,
    ADD COLUMN `success` TINYINT DEFAULT 1 COMMENT '1成功 0失败' AFTER `latency_ms`,
    ADD COLUMN `error_message` VARCHAR(500) DEFAULT NULL COMMENT '失败摘要' AFTER `success`;
