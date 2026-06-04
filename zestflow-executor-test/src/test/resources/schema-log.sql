-- Collector 日志库表结构（H2 test_log 独立数据源）
DROP TABLE IF EXISTS chain_event_payload;
DROP TABLE IF EXISTS invocation_payload;
DROP TABLE IF EXISTS chain_event;

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

CREATE TABLE IF NOT EXISTS chain_event_payload (
    event_id       VARCHAR(64)  NOT NULL PRIMARY KEY,
    params         CLOB         DEFAULT NULL,
    result         CLOB         DEFAULT NULL,
    error_message  CLOB         DEFAULT NULL
);

CREATE TABLE IF NOT EXISTS invocation_payload (
    invocation_id   VARCHAR(64)  NOT NULL PRIMARY KEY,
    source_type     VARCHAR(32)  NOT NULL,
    execution_id    VARCHAR(64)  DEFAULT NULL,
    scene_code      VARCHAR(64)  DEFAULT NULL,
    request_body    CLOB         DEFAULT NULL,
    response_body   CLOB         DEFAULT NULL,
    request_headers CLOB         DEFAULT NULL,
    tenant_id       BIGINT       DEFAULT 1,
    app_code        VARCHAR(50)  DEFAULT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
