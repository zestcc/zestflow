-- ZestFlow E2E Test Schema (H2 MySQL compatible)
-- 测试环境每次重建表，避免 CREATE IF NOT EXISTS 无法追加新列

DROP TABLE IF EXISTS zf_design_binding;
DROP TABLE IF EXISTS zf_chain_version;
DROP TABLE IF EXISTS execution_payload;
DROP TABLE IF EXISTS chain_event;
DROP TABLE IF EXISTS zf_design;
DROP TABLE IF EXISTS zf_chain;

-- ==================== 业务表 ====================

CREATE TABLE IF NOT EXISTS zf_chain (
    code        VARCHAR(64)  NOT NULL PRIMARY KEY,
    chain_key   VARCHAR(128) DEFAULT NULL,
    name        VARCHAR(128) NOT NULL DEFAULT '',
    description VARCHAR(500) DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    version     INT          NOT NULL DEFAULT 1,
    delivery_lifecycle VARCHAR(16) NOT NULL DEFAULT 'bootstrap',
    created_by  VARCHAR(64)  DEFAULT NULL,
    updated_by  VARCHAR(64)  DEFAULT NULL,
    tenant_id   BIGINT       DEFAULT 1,
    app_code    VARCHAR(50)  DEFAULT NULL,
    is_deleted  TINYINT      DEFAULT 0,
    created_at  VARCHAR(32)  DEFAULT NULL,
    updated_at  VARCHAR(32)  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS zf_design (
    code        VARCHAR(64)  NOT NULL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL DEFAULT '',
    description VARCHAR(500) DEFAULT NULL,
    designer    VARCHAR(64)  DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    graph_data  TEXT         DEFAULT NULL,
    chain_data  TEXT         DEFAULT NULL,
    created_by  VARCHAR(64)  DEFAULT NULL,
    updated_by  VARCHAR(64)  DEFAULT NULL,
    tenant_id   BIGINT       DEFAULT 1,
    app_code    VARCHAR(50)  DEFAULT NULL,
    is_deleted  TINYINT      DEFAULT 0,
    created_at  VARCHAR(32)  DEFAULT NULL,
    updated_at  VARCHAR(32)  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS zf_design_binding (
    design_code VARCHAR(64) NOT NULL,
    chain_code  VARCHAR(64) NOT NULL,
    tenant_id   BIGINT      DEFAULT 1,
    app_code    VARCHAR(50) DEFAULT NULL,
    PRIMARY KEY (design_code, chain_code)
);

CREATE TABLE IF NOT EXISTS zf_chain_version (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    chain_code  VARCHAR(64)  NOT NULL,
    version     INT          NOT NULL,
    design_code VARCHAR(64)  DEFAULT NULL,
    graph_data  TEXT         DEFAULT NULL,
    chain_data  TEXT         DEFAULT NULL,
    created_by  VARCHAR(64)  DEFAULT NULL,
    tenant_id   BIGINT       DEFAULT 1,
    app_code    VARCHAR(50)  DEFAULT NULL,
    created_at  VARCHAR(32)  NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chain_version ON zf_chain_version(chain_code, version);

-- ==================== 日志表 ====================

CREATE TABLE IF NOT EXISTS chain_event (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id      VARCHAR(64)  NOT NULL,
    event_type    VARCHAR(32)  NOT NULL,
    execution_id  VARCHAR(64)  DEFAULT NULL,
    chain_id      VARCHAR(64)  DEFAULT NULL,
    chain_name    VARCHAR(128) DEFAULT NULL,
    node_id       VARCHAR(64)  DEFAULT NULL,
    node_name     VARCHAR(128) DEFAULT NULL,
    executor_id   VARCHAR(128) DEFAULT NULL,
    app_name      VARCHAR(64)  DEFAULT NULL,
    app_code      VARCHAR(64)  DEFAULT NULL,
    tenant_id     BIGINT       DEFAULT 1,
    cost_ms       BIGINT       DEFAULT NULL,
    status        TINYINT      DEFAULT NULL,
    timestamp     BIGINT       NOT NULL,
    metadata      TEXT         DEFAULT NULL,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_event_id ON chain_event(event_id);
CREATE INDEX IF NOT EXISTS idx_execution_id ON chain_event(execution_id);
CREATE INDEX IF NOT EXISTS idx_chain_id ON chain_event(chain_id);
CREATE INDEX IF NOT EXISTS idx_timestamp ON chain_event(timestamp);

CREATE TABLE IF NOT EXISTS execution_payload (
    ref_id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    ref_type        VARCHAR(16)  NOT NULL,
    execution_id    VARCHAR(64)  DEFAULT NULL,
    source_type     VARCHAR(32)  DEFAULT NULL,
    scene_code      VARCHAR(64)  DEFAULT NULL,
    params          CLOB         DEFAULT NULL,
    result          CLOB         DEFAULT NULL,
    error_message   CLOB         DEFAULT NULL,
    extra           CLOB         DEFAULT NULL,
    tenant_id       BIGINT       DEFAULT 1,
    app_code        VARCHAR(50)  DEFAULT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_payload_execution_id ON execution_payload(execution_id);
CREATE INDEX IF NOT EXISTS idx_payload_ref_type ON execution_payload(ref_type);

-- 2026-06-04：链事务 E2E 探针表
DROP TABLE IF EXISTS chain_tx_probe;
CREATE TABLE chain_tx_probe (
    id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(64)  NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
