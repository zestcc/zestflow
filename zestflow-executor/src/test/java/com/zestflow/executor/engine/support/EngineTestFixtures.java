package com.zestflow.executor.engine.support;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainLoader;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.engine.ChainInstanceManager;
import com.zestflow.executor.engine.DagSorter;
import com.zestflow.executor.engine.DefaultChainExecutionEngine;
import com.zestflow.executor.engine.NodeRunner;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.registry.ExecutorProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 引擎压测夹具 — 零耗时 mock 节点，隔离编排层开销。
 */
public final class EngineTestFixtures {

    private EngineTestFixtures() {
    }

    public static DefaultChainExecutionEngine instantEngineForLinearChain(int nodeCount) {
        String code = "perf-linear-" + nodeCount;
        ChainDefinition definition = LinearChainFactory.linear(code, nodeCount);

        ChainManager chainManager = mock(ChainManager.class);
        when(chainManager.get(code)).thenReturn(definition);

        NodeRunner nodeRunner = mock(NodeRunner.class);
        when(nodeRunner.execute(any(NodeDefinition.class), any())).thenAnswer(invocation -> {
            NodeDefinition node = invocation.getArgument(0);
            return NodeResultDTO.builder()
                    .nodeId(node.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(0L)
                    .build();
        });

        ExecutorProperties properties = new ExecutorProperties();
        properties.setAppCode("perf-bench");

        ChainLoader chainLoader = mock(ChainLoader.class);
        when(chainLoader.resolveChainDisplayName(any())).thenAnswer(inv -> inv.getArgument(0));

        DefaultChainExecutionEngine engine = new DefaultChainExecutionEngine(
                chainManager,
                new DagSorter(),
                nodeRunner,
                new ChainInstanceManager(),
                null,
                new InterceptorChain(),
                properties
        );
        engine.setChainLoader(chainLoader);
        return engine;
    }
}
