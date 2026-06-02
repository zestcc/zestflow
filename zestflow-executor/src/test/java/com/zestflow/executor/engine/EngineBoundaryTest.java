package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.executor.event.SyncEventPublisher;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.ChainLoader;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EngineBoundaryTest {

    @Mock private ChainManager chainManager;
    @Mock private NodeRunner nodeRunner;
    @Mock private EventCollector eventCollector;
    @Mock private ChainLoader chainLoader;

    private final ChainInstanceManager instanceManager = new ChainInstanceManager();
    private final DagSorter dagSorter = new DagSorter();
    private final InterceptorChain interceptorChain = new InterceptorChain();
    private final ExecutorProperties properties = new ExecutorProperties();

    private DefaultChainExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultChainExecutionEngine(
                chainManager, dagSorter, nodeRunner, instanceManager,
                new SyncEventPublisher(eventCollector), interceptorChain, properties
        );
        engine.setChainLoader(chainLoader);
    }

    @AfterEach
    void tearDown() {
        engine.destroy();
    }

    @Test
    void parallelLayerForksContextAndMergesOutputs() {
        ChainDefinition def = fanOutChain("chain-fork", 3, ChainConstants.DEFAULT_PARALLEL_THRESHOLD);
        when(chainManager.get("chain-fork")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            ChainContext ctx = invocation.getArgument(1);
            ctx.put("out-" + node.getId(), node.getId());
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(1L)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-fork", Map.of("seed", "x"));

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getResultData()).containsEntry("seed", "x");
        assertThat(result.getResultData()).containsEntry("out-B", "B");
        assertThat(result.getResultData()).containsEntry("out-C", "C");
        assertThat(result.getResultData()).containsEntry("out-D", "D");
    }

    @Test
    void belowParallelThresholdUsesSameContextInstance() {
        ChainDefinition def = fanOutChain("chain-seq", 2, ChainConstants.DEFAULT_PARALLEL_THRESHOLD);
        when(chainManager.get("chain-seq")).thenReturn(def);

        List<ChainContext> contexts = new ArrayList<>();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            contexts.add(invocation.getArgument(1));
            NodeDefinition node = invocation.getArgument(0);
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(1L)
                    .build();
        });

        engine.execute("chain-seq", Map.of());

        List<ChainContext> parallelLayerContexts = contexts.stream()
                .skip(1)
                .limit(2)
                .toList();
        assertThat(parallelLayerContexts).hasSize(2);
        assertThat(parallelLayerContexts.get(0)).isSameAs(parallelLayerContexts.get(1));
    }

    @Test
    void atParallelThresholdUsesDistinctForkedContexts() {
        ChainDefinition def = fanOutChain("chain-par", 3, 2);
        when(chainManager.get("chain-par")).thenReturn(def);

        Set<ChainContext> parallelContexts = new HashSet<>();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            ChainContext ctx = invocation.getArgument(1);
            if (!"A".equals(node.getId())) {
                parallelContexts.add(ctx);
            }
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(1L)
                    .build();
        });

        engine.execute("chain-par", Map.of());

        assertThat(parallelContexts).hasSize(3);
    }

    @Test
    void chainTimeoutSkipsRemainingLayers() throws Exception {
        NodeDefinition nodeA = NodeDefinition.builder()
                .id("A")
                .type(ChainConstants.NODE_TYPE_NORMAL)
                .timeout(ChainConstants.NODE_TIMEOUT_UNLIMITED)
                .build();
        NodeDefinition nodeB = NodeDefinition.builder()
                .id("B")
                .type(ChainConstants.NODE_TYPE_NORMAL)
                .timeout(ChainConstants.NODE_TIMEOUT_UNLIMITED)
                .build();
        ChainDefinition def = ChainDefinition.builder()
                .code("chain-timeout")
                .timeout(50L)
                .nodes(Map.of("A", nodeA, "B", nodeB))
                .edges(List.of(new ChainDefinition.ChainEdge("A", "B", null, null)))
                .adjacency(Map.of("A", List.of("B")))
                .inDegree(Map.of("A", 0, "B", 1))
                .predecessors(Map.of("B", List.of("A")))
                .build();
        when(chainManager.get("chain-timeout")).thenReturn(def);

        AtomicInteger invokeCount = new AtomicInteger();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            invokeCount.incrementAndGet();
            if ("A".equals(node.getId())) {
                Thread.sleep(80);
            }
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(80L)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-timeout", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(invokeCount.get()).isEqualTo(1);
        assertThat(result.getNodeResults().get(0).getStatus()).isEqualTo(ChainConstants.NODE_TIMEOUT);
    }

    @Test
    void stopStrategyDoesNotExecuteDownstreamNodes() {
        ChainDefinition def = linearChain("chain-stop", 60_000L);
        def = ChainDefinition.builder()
                .code(def.getCode())
                .nodes(def.getNodes())
                .edges(def.getEdges())
                .adjacency(def.getAdjacency())
                .inDegree(def.getInDegree())
                .predecessors(def.getPredecessors())
                .timeout(def.getTimeout())
                .errorStrategy(ChainConstants.ERROR_STRATEGY_STOP)
                .build();
        when(chainManager.get("chain-stop")).thenReturn(def);

        AtomicInteger invokeCount = new AtomicInteger();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            invokeCount.incrementAndGet();
            if ("A".equals(node.getId())) {
                return NodeResultDTO.builder()
                        .nodeId("A")
                        .status(ChainConstants.NODE_FAILED)
                        .errorMessage("模拟失败")
                        .build();
            }
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-stop", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(invokeCount.get()).isEqualTo(1);
        assertThat(result.getErrorMessage()).contains("模拟失败");
    }

    @Test
    void nodeTimeoutFailsSlowNode() throws Exception {
        NodeDefinition slowNode = NodeDefinition.builder()
                .id("A")
                .type(ChainConstants.NODE_TYPE_NORMAL)
                .timeout(50L)
                .build();
        ChainDefinition def = ChainDefinition.builder()
                .code("node-timeout")
                .timeout(60_000L)
                .nodes(Map.of("A", slowNode))
                .edges(java.util.List.of())
                .adjacency(Map.of())
                .inDegree(Map.of("A", 0))
                .predecessors(Map.of())
                .build();
        when(chainManager.get("node-timeout")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            Thread.sleep(200);
            return NodeResultDTO.builder()
                    .nodeId("A")
                    .status(ChainConstants.NODE_SUCCESS)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("node-timeout", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(result.getNodeResults()).hasSize(1);
        assertThat(result.getNodeResults().get(0).getStatus()).isEqualTo(ChainConstants.NODE_TIMEOUT);
    }

    @Test
    void continueStrategyMarksPartialFailureAndCompletes() {
        ChainDefinition def = linearChain("chain-continue", 60_000L);
        def = ChainDefinition.builder()
                .code(def.getCode())
                .nodes(def.getNodes())
                .edges(def.getEdges())
                .adjacency(def.getAdjacency())
                .inDegree(def.getInDegree())
                .predecessors(def.getPredecessors())
                .timeout(def.getTimeout())
                .errorStrategy(ChainConstants.ERROR_STRATEGY_CONTINUE)
                .build();
        when(chainManager.get("chain-continue")).thenReturn(def);

        AtomicInteger invokeCount = new AtomicInteger();
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            invokeCount.incrementAndGet();
            if ("B".equals(node.getId())) {
                return NodeResultDTO.builder()
                        .nodeId("B")
                        .status(ChainConstants.NODE_FAILED)
                        .errorMessage("中段失败")
                        .build();
            }
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-continue", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(invokeCount.get()).isEqualTo(3);
        assertThat(result.getResultData()).containsEntry(ChainConstants.CTX_PARTIAL_FAILURE, true);
        assertThat(result.getResultData().get(ChainConstants.CTX_FAILED_NODE_IDS)).isEqualTo(List.of("B"));
    }

    @Test
    void compensateStrategyRunsReverseCompensation() {
        ChainDefinition def = linearChain("chain-comp", 60_000L);
        def = ChainDefinition.builder()
                .code(def.getCode())
                .nodes(def.getNodes())
                .edges(def.getEdges())
                .adjacency(def.getAdjacency())
                .inDegree(def.getInDegree())
                .predecessors(def.getPredecessors())
                .timeout(def.getTimeout())
                .errorStrategy(ChainConstants.ERROR_STRATEGY_COMPENSATE)
                .build();
        when(chainManager.get("chain-comp")).thenReturn(def);

        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            if ("B".equals(node.getId())) {
                return NodeResultDTO.builder()
                        .nodeId("B")
                        .status(ChainConstants.NODE_FAILED)
                        .errorMessage("触发补偿")
                        .build();
            }
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .build();
        });
        when(nodeRunner.compensate(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_COMPENSATED)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-comp", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_COMPENSATED);
        verify(nodeRunner).compensate(argThat(n -> "A".equals(n.getId())), any());
        verify(nodeRunner, never()).compensate(argThat(n -> "B".equals(n.getId())), any());
    }

    @Test
    void stopDuringLayerExecutionTerminatesChain() {
        ChainDefinition def = linearChain("chain-stop-mid", 60_000L);
        when(chainManager.get("chain-stop-mid")).thenReturn(def);

        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            if ("A".equals(node.getId())) {
                instanceManager.listByChainCode("chain-stop-mid").forEach(ChainInstance::markStopped);
            }
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-stop-mid", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_STOPPED);
    }

    private static ChainDefinition linearChain(String code, long timeoutMs) {
        Map<String, NodeDefinition> nodes = Map.of(
                "A", nodeDef("A"),
                "B", nodeDef("B"),
                "C", nodeDef("C")
        );
        Map<String, List<String>> adj = Map.of("A", List.of("B"), "B", List.of("C"));
        Map<String, Integer> inDegree = Map.of("A", 0, "B", 1, "C", 1);
        Map<String, List<String>> predecessors = Map.of("B", List.of("A"), "C", List.of("B"));
        return ChainDefinition.builder()
                .code(code)
                .nodes(nodes)
                .edges(List.of(
                        new ChainEdge("A", "B", null, null),
                        new ChainEdge("B", "C", null, null)))
                .adjacency(adj)
                .inDegree(inDegree)
                .predecessors(predecessors)
                .timeout(timeoutMs)
                .build();
    }

    private static ChainDefinition fanOutChain(String code, int parallelCount, int parallelThreshold) {
        Map<String, NodeDefinition> nodes = new HashMap<>();
        nodes.put("A", nodeDef("A"));

        List<String> parallelIds = new ArrayList<>();
        for (int i = 0; i < parallelCount; i++) {
            String id = String.valueOf((char) ('B' + i));
            parallelIds.add(id);
            nodes.put(id, nodeDef(id));
        }

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", parallelIds);

        Map<String, Integer> inDegree = new HashMap<>();
        inDegree.put("A", 0);
        parallelIds.forEach(id -> inDegree.put(id, 1));

        Map<String, List<String>> predecessors = new HashMap<>();
        parallelIds.forEach(id -> predecessors.put(id, List.of("A")));

        List<ChainEdge> edges = parallelIds.stream()
                .map(id -> new ChainEdge("A", id, null, null))
                .toList();

        return ChainDefinition.builder()
                .code(code)
                .nodes(nodes)
                .edges(edges)
                .adjacency(adj)
                .inDegree(inDegree)
                .predecessors(predecessors)
                .parallelThreshold(parallelThreshold)
                .build();
    }

    private static NodeDefinition nodeDef(String id) {
        return NodeDefinition.builder().id(id).type(ChainConstants.NODE_TYPE_NORMAL).build();
    }
}
