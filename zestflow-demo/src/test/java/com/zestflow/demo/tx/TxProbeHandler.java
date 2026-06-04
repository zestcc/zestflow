package com.zestflow.demo.tx;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 链事务 E2E 探针元件 — 仅测试使用，由 {@link com.zestflow.demo.ChainTransactionE2ETest} Import 注册。
 */
@ZestComponent("txProbe")
public class TxProbeHandler {

    private final JdbcTemplate jdbcTemplate;

    public TxProbeHandler(@Qualifier("executorJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @ZestExecute(value = "txInsertA", description = "写入探针 A")
    public void insertA() {
        insertProbe("A");
    }

    @ZestExecute(value = "txInsertB", description = "写入探针 B")
    public void insertB() {
        insertProbe("B");
    }

    @ZestExecute(value = "txProbeFail", description = "故意失败以触发回滚")
    public void fail() {
        throw new IllegalStateException("tx probe intentional failure");
    }

    private void insertProbe(String probeKey) {
        jdbcTemplate.update("INSERT INTO chain_tx_probe (probe_key) VALUES (?)", probeKey);
    }
}
