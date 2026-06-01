package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultChainExecutionEngineIntegrationTest {

    @Mock private ChainManager chainManager;
    @Mock private NodeRunner nodeRunner;
    @Mock private EventCollector eventCollector;
    @Captor private ArgumentCaptor<ChainEvent> eventCaptor;

    private final ChainInstanceManager instanceManager = new ChainInstanceManager();
    private final DagSorter dagSorter = new DagSorter();
    private final InterceptorChain interceptorChain = new InterceptorChain();
    private final ExecutorProperties properties = new ExecutorProperties();

    private DefaultChainExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DefaultChainExecutionEngine(
                chainManager, dagSorter, nodeRunner, instanceManager,
                eventCollector, interceptorChain, properties
        );
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void singleNodeExecutesSuccessfully() {
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", nodeDef("A", ChainConstants.NODE_TYPE_NORMAL)))
                .edges(List.of())
                .adjacency(new HashMap<>())
                .inDegree(Map.of("A", 0))
                .predecessors(new HashMap<>())
                .build();
        when(chainManager.get("chain1")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenReturn(
                NodeResultDTO.builder().nodeId("A").status(ChainConstants.NODE_SUCCESS).costMs(10L).build()
        );

        ChainExecuteResultDTO result = engine.execute("chain1", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getInstanceId()).isNotNull();
        assertThat(result.getNodeResults()).hasSize(1);
        assertThat(result.getNodeResults().get(0).getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void publishesChainEventsOnSuccess() {
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", nodeDef("A", ChainConstants.NODE_TYPE_NORMAL)))
                .edges(List.of())
                .adjacency(new HashMap<>())
                .inDegree(Map.of("A", 0))
                .predecessors(new HashMap<>())
                .build();
        when(chainManager.get("chain1")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenReturn(
                NodeResultDTO.builder().nodeId("A").status(ChainConstants.NODE_SUCCESS).costMs(10L).build()
        );

        engine.execute("chain1", Map.of());

        verify(eventCollector, atLeast(2)).collect(eventCaptor.capture());
        List<ChainEvent.EventType> types = eventCaptor.getAllValues().stream()
                .map(ChainEvent::getEventType)
                .collect(Collectors.toList());
        assertThat(types).contains(
                ChainEvent.EventType.CHAIN_STARTED,
                ChainEvent.EventType.CHAIN_COMPLETED
        );
    }

    @Test
    void chainNotFoundReturnsFailed() {
        when(chainManager.get("non-existent")).thenReturn(null);

        ChainExecuteResultDTO result = engine.execute("non-existent", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_FAILED);
        assertThat(result.getErrorMessage()).contains("链定义不存在");
    }

    @Test
    void nodeFailurePublishesFailedEvent() {
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", nodeDef("A", ChainConstants.NODE_TYPE_NORMAL)))
                .edges(List.of())
                .adjacency(new HashMap<>())
                .inDegree(Map.of("A", 0))
                .predecessors(new HashMap<>())
                .errorStrategy(ChainConstants.ERROR_STRATEGY_STOP)
                .build();
        when(chainManager.get("chain1")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenReturn(
                NodeResultDTO.builder().nodeId("A").status(ChainConstants.NODE_FAILED)
                        .errorMessage("模拟失败").build()
        );

        engine.execute("chain1", Map.of());

        verify(eventCollector, atLeast(2)).collect(eventCaptor.capture());
        List<ChainEvent.EventType> types = eventCaptor.getAllValues().stream()
                .map(ChainEvent::getEventType)
                .collect(Collectors.toList());
        assertThat(types).contains(
                ChainEvent.EventType.CHAIN_STARTED,
                ChainEvent.EventType.CHAIN_FAILED
        );
    }

    @Test
    void linearChainExecutesThreeNodes() {
        Map<String, NodeDefinition> nodes = new HashMap<>();
        nodes.put("A", nodeDef("A", ChainConstants.NODE_TYPE_NORMAL));
        nodes.put("B", nodeDef("B", ChainConstants.NODE_TYPE_NORMAL));
        nodes.put("C", nodeDef("C", ChainConstants.NODE_TYPE_NORMAL));

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B"));
        adj.put("B", List.of("C"));

        Map<String, Integer> inDegree = new HashMap<>();
        inDegree.put("A", 0);
        inDegree.put("B", 1);
        inDegree.put("C", 1);

        Map<String, List<String>> pred = new HashMap<>();
        pred.put("B", List.of("A"));
        pred.put("C", List.of("B"));

        ChainDefinition def = ChainDefinition.builder()
                .code("chain-abc")
                .nodes(nodes)
                .edges(List.of(new ChainEdge("A", "B", null, null), new ChainEdge("B", "C", null, null)))
                .adjacency(adj)
                .inDegree(inDegree)
                .predecessors(pred)
                .build();

        when(chainManager.get("chain-abc")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition nd = invocation.getArgument(0);
            return NodeResultDTO.builder().nodeId(nd.getId()).status(ChainConstants.NODE_SUCCESS).costMs(5L).build();
        });

        ChainExecuteResultDTO result = engine.execute("chain-abc", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
        assertThat(result.getNodeResults()).extracting(nr -> nr.getNodeId())
                .containsExactly("A", "B", "C");
    }

    @Test
    void parallelNodesExecuteConcurrently() {
        Map<String, NodeDefinition> nodes = new HashMap<>();
        nodes.put("A", nodeDef("A", ChainConstants.NODE_TYPE_NORMAL));
        nodes.put("B", nodeDef("B", ChainConstants.NODE_TYPE_NORMAL));
        nodes.put("C", nodeDef("C", ChainConstants.NODE_TYPE_NORMAL));

        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B", "C"));

        Map<String, Integer> inDegree = new HashMap<>();
        inDegree.put("A", 0);
        inDegree.put("B", 1);
        inDegree.put("C", 1);

        ChainDefinition def = ChainDefinition.builder()
                .code("chain-parallel")
                .nodes(nodes)
                .edges(List.of(new ChainEdge("A", "B", null, null), new ChainEdge("A", "C", null, null)))
                .adjacency(adj)
                .inDegree(inDegree)
                .predecessors(Map.of("B", List.of("A"), "C", List.of("A")))
                .build();

        when(chainManager.get("chain-parallel")).thenReturn(def);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenReturn(
                NodeResultDTO.builder().nodeId("any").status(ChainConstants.NODE_SUCCESS).costMs(5L).build()
        );

        ChainExecuteResultDTO result = engine.execute("chain-parallel", Map.of());

        assertThat(result.getStatus()).isEqualTo(ChainConstants.CHAIN_SUCCESS);
        assertThat(result.getNodeResults()).hasSize(3);
    }

    private static NodeDefinition nodeDef(String id, String type) {
        return NodeDefinition.builder().id(id).type(type).build();
    }
}
