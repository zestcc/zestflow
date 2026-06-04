DROP TABLE IF EXISTS chain_tx_probe;
CREATE TABLE chain_tx_probe (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    probe_key VARCHAR(64) NOT NULL
);
