package com.zestflow.executor.engine.support;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.NodeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 线性链工厂 — 压测 / JMH 微基准用。
 */
public final class LinearChainFactory {

    private LinearChainFactory() {
    }

    public static ChainDefinition linear(String code, int nodeCount) {
        if (nodeCount < 1) {
            throw new IllegalArgumentException("nodeCount must be >= 1");
        }

        Map<String, NodeDefinition> nodes = new LinkedHashMap<>();
        List<ChainEdge> edges = new ArrayList<>();
        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> predecessors = new HashMap<>();

        for (int i = 0; i < nodeCount; i++) {
            String id = "N" + i;
            nodes.put(id, NodeDefinition.builder()
                    .id(id)
                    .type(ChainConstants.NODE_TYPE_NORMAL)
                    .build());
            inDegree.put(id, i == 0 ? 0 : 1);
            if (i > 0) {
                String prev = "N" + (i - 1);
                edges.add(new ChainEdge(prev, id, null, null));
                adjacency.computeIfAbsent(prev, k -> new ArrayList<>()).add(id);
                predecessors.put(id, List.of(prev));
            }
        }

        return ChainDefinition.builder()
                .code(code)
                .nodes(nodes)
                .edges(edges)
                .adjacency(adjacency)
                .inDegree(inDegree)
                .predecessors(predecessors)
                .build();
    }
}
