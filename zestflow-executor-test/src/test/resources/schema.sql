-- ZestFlow E2E Test Schema (H2 MySQL compatible)

-- ==================== 业务表 ====================

CREATE TABLE IF NOT EXISTS zf_chain (
    code        VARCHAR(64)  NOT NULL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL DEFAULT '',
    description VARCHAR(500) DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    design_code VARCHAR(64)  DEFAULT NULL,
    version     INT          NOT NULL DEFAULT 1,
    created_by  VARCHAR(64)  DEFAULT NULL,
    updated_by  VARCHAR(64)  DEFAULT NULL,
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
    is_deleted  TINYINT      DEFAULT 0,
    created_at  VARCHAR(32)  DEFAULT NULL,
    updated_at  VARCHAR(32)  DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS zf_design_binding (
    design_code VARCHAR(64) NOT NULL,
    chain_code  VARCHAR(64) NOT NULL,
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
    params        TEXT         DEFAULT NULL,
    result        TEXT         DEFAULT NULL,
    error_message TEXT         DEFAULT NULL,
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
