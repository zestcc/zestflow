package com.zestflow.demo;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainEdgeDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.demo.tx.TxProbeHandler;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.ChainRuntimeRegistrar;
import com.zestflow.executor.engine.ChainExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 链级 Spring 事务 E2E — 设计器 config.transaction → 引擎 TransactionTemplate → JDBC 回滚/提交。
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = DemoApplication.class)
@ActiveProfiles("test")
@Import({ZestFlowE2ETest.InMemoryCollectorConfig.class, TxProbeHandler.class})
class ChainTransactionE2ETest {

    @Autowired
    private ChainExecutionEngine chainExecutionEngine;

    @Autowired
    private ChainManager chainManager;

    @Autowired
    private ChainDefinitionBuilder chainDefinitionBuilder;

    @Autowired
    private ChainRuntimeRegistrar chainRuntimeRegistrar;

    @Autowired
    @Qualifier("executorJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM chain_tx_probe");
    }

    @Test
    void chainTransactionRollsBackOnFailure() {
        ChainExecuteResultDTO result = execute("tx-e2e-rollback", List.of(
                node("A", "txInsertA"),
                node("B", "txProbeFail")
        ), List.of(edge("A", "B")), txChainConfig(true));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(countProbes()).isZero();
    }

    @Test
    void chainTransactionCommitsOnSuccess() {
        ChainExecuteResultDTO result = execute("tx-e2e-commit", List.of(
                node("A", "txInsertA")
        ), List.of(), txChainConfig(true));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(countProbes()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT probe_key FROM chain_tx_probe LIMIT 1", String.class)).isEqualTo("A");
    }

    @Test
    void nodeRequiresNewSurvivesOuterRollback() {
        ChainNodeDTO nodeB = ChainNodeDTO.builder()
                .id("B")
                .label("B")
                .type("NORMAL")
                .component("txInsertB")
                .config(Map.of("transactionPropagation", "REQUIRES_NEW"))
                .build();

        ChainExecuteResultDTO result = execute("tx-e2e-requires-new", List.of(
                node("A", "txInsertA"),
                nodeB,
                node("C", "txProbeFail")
        ), List.of(edge("A", "B"), edge("B", "C")), txChainConfig(true));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(countProbes()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT probe_key FROM chain_tx_probe LIMIT 1", String.class)).isEqualTo("B");
    }

    @Test
    void chainWithoutTransactionDoesNotUseChainLevelTx() {
        ChainExecuteResultDTO result = execute("tx-e2e-no-tx", List.of(
                node("A", "txInsertA"),
                node("B", "txProbeFail")
        ), List.of(edge("A", "B")), Map.of("errorStrategy", ChainConstants.ERROR_STRATEGY_STOP));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        // 无链级事务时 insert 不在同一 Spring 事务中，A 的写入可能已提交
        assertThat(countProbes()).isEqualTo(1);
    }

    private int countProbes() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chain_tx_probe", Integer.class);
        return count != null ? count : 0;
    }

    private static Map<String, Object> txChainConfig(boolean enabled) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("errorStrategy", ChainConstants.ERROR_STRATEGY_STOP);
        if (enabled) {
            config.put("transaction", Map.of("enabled", true, "propagation", "REQUIRED"));
        }
        return config;
    }

    private ChainExecuteResultDTO execute(String code, List<ChainNodeDTO> nodes, List<ChainEdgeDTO> edges,
                                          Map<String, Object> config) {
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(code)
                .version(1)
                .nodes(nodes)
                .edges(edges)
                .config(config)
                .build();
        chainManager.load(chainDefinitionBuilder.build(dto));
        chainRuntimeRegistrar.ensurePublished(code);
        return chainExecutionEngine.execute(code, Map.of());
    }

    private static ChainNodeDTO node(String id, String component) {
        return ChainNodeDTO.builder().id(id).label(id).type("NORMAL").component(component).build();
    }

    private static ChainEdgeDTO edge(String source, String target) {
        return ChainEdgeDTO.builder().source(source).target(target).build();
    }
}
