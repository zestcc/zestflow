package com.zestflow.executor.engine;

import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.NodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DagSorterTest {

    private final DagSorter sorter = new DagSorter();

    @Test
    void singleNode() {
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", node("A")))
                .edges(List.of())
                .adjacency(new HashMap<>())
                .inDegree(Map.of("A", 0))
                .predecessors(new HashMap<>())
                .build();
        List<List<String>> layers = sorter.sort(def);
        assertEquals(1, layers.size());
        assertEquals(List.of("A"), layers.get(0));
    }

    @Test
    void linearChain() {
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", node("A"), "B", node("B"), "C", node("C")))
                .edges(List.of(new ChainEdge("A", "B", null, null), new ChainEdge("B", "C", null, null)))
                .adjacency(new HashMap<>(Map.of("A", List.of("B"), "B", List.of("C"))))
                .inDegree(new HashMap<>(Map.of("A", 0, "B", 1, "C", 1)))
                .predecessors(new HashMap<>(Map.of("B", List.of("A"), "C", List.of("B"))))
                .build();

        List<List<String>> layers = sorter.sort(def);
        assertEquals(3, layers.size());
        assertEquals(List.of("A"), layers.get(0));
        assertEquals(List.of("B"), layers.get(1));
        assertEquals(List.of("C"), layers.get(2));
    }

    @Test
    void parallelNodes() {
        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B", "C"));
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", node("A"), "B", node("B"), "C", node("C")))
                .edges(List.of(new ChainEdge("A", "B", null, null), new ChainEdge("A", "C", null, null)))
                .adjacency(adj)
                .inDegree(new HashMap<>(Map.of("A", 0, "B", 1, "C", 1)))
                .predecessors(new HashMap<>(Map.of("B", List.of("A"), "C", List.of("A"))))
                .build();

        List<List<String>> layers = sorter.sort(def);
        assertEquals(2, layers.size());
        assertEquals(List.of("A"), layers.get(0));
        assertTrue(layers.get(1).contains("B"));
        assertTrue(layers.get(1).contains("C"));
        assertEquals(2, layers.get(1).size());
    }

    @Test
    void diamondDag() {
        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B", "C"));
        adj.put("B", List.of("D"));
        adj.put("C", List.of("D"));
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", node("A"), "B", node("B"), "C", node("C"), "D", node("D")))
                .edges(List.of(
                        new ChainEdge("A", "B", null, null),
                        new ChainEdge("A", "C", null, null),
                        new ChainEdge("B", "D", null, null),
                        new ChainEdge("C", "D", null, null)
                ))
                .adjacency(adj)
                .inDegree(new HashMap<>(Map.of("A", 0, "B", 1, "C", 1, "D", 2)))
                .predecessors(new HashMap<>(Map.of("B", List.of("A"), "C", List.of("A"), "D", List.of("B", "C"))))
                .build();

        List<List<String>> layers = sorter.sort(def);
        assertEquals(3, layers.size());
        assertEquals(List.of("A"), layers.get(0));
        assertEquals(2, layers.get(1).size());
        assertEquals(List.of("D"), layers.get(2));
    }

    @Test
    void cycleDetection() {
        Map<String, List<String>> adj = new HashMap<>();
        adj.put("A", List.of("B"));
        adj.put("B", List.of("C"));
        adj.put("C", List.of("A"));
        ChainDefinition def = ChainDefinition.builder()
                .code("chain1")
                .nodes(Map.of("A", node("A"), "B", node("B"), "C", node("C")))
                .edges(List.of(
                        new ChainEdge("A", "B", null, null),
                        new ChainEdge("B", "C", null, null),
                        new ChainEdge("C", "A", null, null)
                ))
                .adjacency(adj)
                .inDegree(new HashMap<>(Map.of("A", 1, "B", 1, "C", 1)))
                .predecessors(new HashMap<>(Map.of("B", List.of("A"), "C", List.of("B"), "A", List.of("C"))))
                .build();

        // 环路检测：DagSorter.sort 目前只 log 警告不抛异常，需验证有节点未处理
        List<List<String>> layers = sorter.sort(def);
        long processed = layers.stream().mapToInt(List::size).sum();
        assertTrue(processed < 3, "环路导致部分节点无法处理");
    }

    private NodeDefinition node(String id) {
        return NodeDefinition.builder().id(id).build();
    }
}
