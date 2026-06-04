package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.common.protocol.ChainTransactionConfig;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.ChainLoader;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.event.SyncEventPublisher;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 链级 Spring 事务编排 — 真实 TransactionManager + Mock NodeRunner。
 */
@ExtendWith(MockitoExtension.class)
class ChainTransactionEngineTest {

    @Mock private ChainManager chainManager;
    @Mock private NodeRunner nodeRunner;
    @Mock private EventCollector eventCollector;
    @Mock private ChainLoader chainLoader;

    private final ChainInstanceManager instanceManager = new ChainInstanceManager();
    private final DagSorter dagSorter = new DagSorter();
    private final InterceptorChain interceptorChain = new InterceptorChain();
    private final ExecutorProperties properties = new ExecutorProperties();

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private ChainTransactionExecutor chainTransactionExecutor;
    private DefaultChainExecutionEngine engine;

    @BeforeEach
    void setUp() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:chain-tx-test-schema.sql")
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        chainTransactionExecutor = new ChainTransactionExecutor(new DataSourceTransactionManager(dataSource));
        engine = new DefaultChainExecutionEngine(
                chainManager, dagSorter, nodeRunner, instanceManager,
                new SyncEventPublisher(eventCollector), interceptorChain, properties,
                chainTransactionExecutor);
        engine.setChainLoader(chainLoader);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.destroy();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void chainTransactionActiveDuringNodeExecution() {
        ChainDefinition def = linearChain("tx-active", txEnabled());
        when(chainManager.get("tx-active")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            NodeDefinition nd = invocation.getArgument(0);
            return success(nd.getId());
        });

        ChainExecuteResultDTO result = engine.execute("tx-active", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
    }

    @Test
    void chainTransactionRollsBackOnStopFailure() {
        ChainDefinition def = linearChain("tx-rollback", txEnabled(), Map.of(
                "A", NodeDefinition.builder().id("A").type(ChainConstants.NODE_TYPE_NORMAL).build(),
                "B", NodeDefinition.builder().id("B").type(ChainConstants.NODE_TYPE_NORMAL).build()
        ), List.of(new ChainEdge("A", "B", null, null)));
        when(chainManager.get("tx-rollback")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition nd = invocation.getArgument(0);
            jdbcTemplate.update("INSERT INTO chain_tx_probe (probe_key) VALUES (?)", nd.getId());
            if ("B".equals(nd.getId())) {
                return NodeResultDTO.builder()
                        .nodeId("B")
                        .status(ChainConstants.NODE_FAILED)
                        .errorMessage("模拟失败")
                        .build();
            }
            return success(nd.getId());
        });

        ChainExecuteResultDTO result = engine.execute("tx-rollback", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chain_tx_probe", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void chainTransactionCommitsOnSuccess() {
        ChainDefinition def = linearChain("tx-commit", txEnabled());
        when(chainManager.get("tx-commit")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition nd = invocation.getArgument(0);
            jdbcTemplate.update("INSERT INTO chain_tx_probe (probe_key) VALUES (?)", nd.getId());
            return success(nd.getId());
        });

        ChainExecuteResultDTO result = engine.execute("tx-commit", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chain_tx_probe", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void nodeRequiresNewCommitsWhenOuterRollsBack() {
        Map<String, NodeDefinition> nodes = new HashMap<>();
        nodes.put("A", NodeDefinition.builder().id("A").type(ChainConstants.NODE_TYPE_NORMAL).build());
        nodes.put("B", NodeDefinition.builder().id("B").type(ChainConstants.NODE_TYPE_NORMAL)
                .transactionPropagation("REQUIRES_NEW").build());
        nodes.put("C", NodeDefinition.builder().id("C").type(ChainConstants.NODE_TYPE_NORMAL).build());

        ChainDefinition def = linearChain("tx-requires-new", txEnabled(), nodes, List.of(
                new ChainEdge("A", "B", null, null),
                new ChainEdge("B", "C", null, null)
        ));
        when(chainManager.get("tx-requires-new")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition nd = invocation.getArgument(0);
            jdbcTemplate.update("INSERT INTO chain_tx_probe (probe_key) VALUES (?)", nd.getId());
            if ("C".equals(nd.getId())) {
                return NodeResultDTO.builder()
                        .nodeId("C")
                        .status(ChainConstants.NODE_FAILED)
                        .errorMessage("终止")
                        .build();
            }
            return success(nd.getId());
        });

        engine.execute("tx-requires-new", Map.of());

        List<String> keys = jdbcTemplate.queryForList("SELECT probe_key FROM chain_tx_probe ORDER BY id", String.class);
        assertThat(keys).containsExactly("B");
    }

    @Test
    void chainTransactionForcesSequentialParallelLayer() {
        ChainDefinition def = forkChain("tx-sequential", txEnabled(), 3);
        when(chainManager.get("tx-sequential")).thenReturn(def);
        List<String> order = new CopyOnWriteArrayList<>();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition nd = invocation.getArgument(0);
            order.add(nd.getId());
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return success(nd.getId());
        });

        engine.execute("tx-sequential", Map.of());

        assertThat(order).containsSubsequence("A", "B", "C", "D");
    }

    @Test
    void parallelLayerWithoutTransactionMayRunConcurrently() throws InterruptedException {
        ChainDefinition def = forkChain("tx-parallel", ChainTransactionConfig.disabled(), 2);
        when(chainManager.get("tx-parallel")).thenReturn(def);
        List<Long> threadIds = new CopyOnWriteArrayList<>();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition nd = invocation.getArgument(0);
            if ("B".equals(nd.getId()) || "C".equals(nd.getId()) || "D".equals(nd.getId())) {
                threadIds.add(Thread.currentThread().getId());
                Thread.sleep(40);
            }
            return success(nd.getId());
        });

        engine.execute("tx-parallel", Map.of());

        assertThat(threadIds.size()).isGreaterThanOrEqualTo(2);
        assertThat(threadIds.stream().distinct().count()).isGreaterThanOrEqualTo(2);
    }

    private static ChainTransactionConfig txEnabled() {
        return ChainTransactionConfig.builder().enabled(true).propagation("REQUIRED").build();
    }

    private static NodeResultDTO success(String nodeId) {
        return NodeResultDTO.builder().nodeId(nodeId).status(ChainConstants.NODE_SUCCESS).costMs(1L).build();
    }

    private static ChainDefinition linearChain(String code, ChainTransactionConfig txConfig) {
        Map<String, NodeDefinition> nodes = Map.of(
                "A", NodeDefinition.builder().id("A").type(ChainConstants.NODE_TYPE_NORMAL).build()
        );
        return ChainDefinition.builder()
                .code(code)
                .nodes(nodes)
                .edges(List.of())
                .adjacency(new HashMap<>())
                .inDegree(Map.of("A", 0))
                .predecessors(new HashMap<>())
                .errorStrategy(ChainConstants.ERROR_STRATEGY_STOP)
                .transactionConfig(txConfig)
                .build();
    }

    private static ChainDefinition linearChain(String code, ChainTransactionConfig txConfig,
                                                Map<String, NodeDefinition> nodes, List<ChainEdge> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> pred = new HashMap<>();
        for (NodeDefinition node : nodes.values()) {
            inDegree.put(node.getId(), 0);
        }
        for (ChainEdge edge : edges) {
            adj.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
            inDegree.merge(edge.getTarget(), 1, Integer::sum);
            pred.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge.getSource());
        }
        return ChainDefinition.builder()
                .code(code)
                .nodes(nodes)
                .edges(edges)
                .adjacency(adj)
                .inDegree(inDegree)
                .predecessors(pred)
                .errorStrategy(ChainConstants.ERROR_STRATEGY_STOP)
                .transactionConfig(txConfig)
                .parallelThreshold(2)
                .build();
    }

    /** A → B,C,D（同层 3 节点，parallelThreshold=2 时可并行） */
    private static ChainDefinition forkChain(String code, ChainTransactionConfig txConfig, int parallelThreshold) {
        Map<String, NodeDefinition> nodes = new HashMap<>();
        nodes.put("A", NodeDefinition.builder().id("A").type(ChainConstants.NODE_TYPE_NORMAL).build());
        nodes.put("B", NodeDefinition.builder().id("B").type(ChainConstants.NODE_TYPE_NORMAL).build());
        nodes.put("C", NodeDefinition.builder().id("C").type(ChainConstants.NODE_TYPE_NORMAL).build());
        nodes.put("D", NodeDefinition.builder().id("D").type(ChainConstants.NODE_TYPE_NORMAL).build());

        List<ChainEdge> edges = List.of(
                new ChainEdge("A", "B", null, null),
                new ChainEdge("A", "C", null, null),
                new ChainEdge("A", "D", null, null)
        );
        Map<String, List<String>> adj = Map.of("A", List.of("B", "C", "D"));
        Map<String, Integer> inDegree = Map.of("A", 0, "B", 1, "C", 1, "D", 1);
        Map<String, List<String>> pred = Map.of(
                "B", List.of("A"), "C", List.of("A"), "D", List.of("A"));

        return ChainDefinition.builder()
                .code(code)
                .nodes(nodes)
                .edges(edges)
                .adjacency(adj)
                .inDegree(inDegree)
                .predecessors(pred)
                .errorStrategy(ChainConstants.ERROR_STRATEGY_STOP)
                .transactionConfig(txConfig)
                .parallelThreshold(parallelThreshold)
                .build();
    }
}
