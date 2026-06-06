package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.event.SyncEventPublisher;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeRunnerExtendedTypesTest {

    @Mock ComponentScanner componentScanner;
    @Mock com.zestflow.common.spi.EventCollector eventCollector;
    @Mock InterceptorChain interceptorChain;
    @Mock LifecycleExecutor lifecycleExecutor;
    @Mock RetryExecutor retryExecutor;
    @Mock com.zestflow.executor.chain.ChainManager chainManager;
    @Mock com.zestflow.executor.registry.ExecutorProperties executorProperties;

    private NodeRunner nodeRunner;

    @BeforeEach
    void setUp() {
        when(executorProperties.getAppCode()).thenReturn("test-app");
        when(executorProperties.getHost()).thenReturn("127.0.0.1");
        when(executorProperties.getPort()).thenReturn(9999);
        when(executorProperties.getAppName()).thenReturn("test-app");
        nodeRunner = new NodeRunner(componentScanner, new SyncEventPublisher(eventCollector),
                interceptorChain, lifecycleExecutor, retryExecutor, chainManager, executorProperties);
    }

    @Test
    void mqProducerExecutesViaLifecycle() {
        NodeDefinition nodeDef = nodeDef("mq1", ChainConstants.NODE_TYPE_MQ_PRODUCER, "sendOrderCreatedMsg");
        ChainContext ctx = new ChainContext("inst", "chain", Map.of());
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("OK");

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void mqConsumerExecutesViaLifecycle() {
        NodeDefinition nodeDef = nodeDef("mq2", ChainConstants.NODE_TYPE_MQ_CONSUMER, "consumeOrderCreatedMsg");
        ChainContext ctx = new ChainContext("inst", "chain", Map.of());
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("OK");

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void tryCatchSetsCatchBranchOnFailure() {
        NodeDefinition nodeDef = nodeDef("tc1", ChainConstants.NODE_TYPE_TRY_CATCH, "validateUser");
        ChainContext ctx = new ChainContext("inst", "chain", Map.of());
        when(lifecycleExecutor.execute(any(), any())).thenThrow(new RuntimeException("boom"));

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        assertThat(ctx.get("_branch")).isEqualTo("Catch");
        assertThat(ctx.get("_try_catch_error")).isEqualTo("boom");
    }

    @Test
    void approvalPendingReturnsSkipped() {
        NodeDefinition nodeDef = nodeDef("ap1", ChainConstants.NODE_TYPE_APPROVAL, "validateUser");
        ChainContext ctx = new ChainContext("inst", "chain", Map.of());

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SKIPPED);
        assertThat(ctx.get("_approval_pending")).isEqualTo("ap1");
    }

    @Test
    void delayCompletesWithShortTimeout() {
        NodeDefinition nodeDef = nodeDef("d1", ChainConstants.NODE_TYPE_DELAY, null);
        nodeDef = NodeDefinition.builder().id("d1").type(ChainConstants.NODE_TYPE_DELAY).timeout(50L).build();
        ChainContext ctx = new ChainContext("inst", "chain", Map.of());

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        assertThat(result.getReturnValue()).isEqualTo("DELAYED_50ms");
    }

    @Test
    void forkJoinExecuteWithLifecycle() {
        NodeDefinition fork = nodeDef("fk1", ChainConstants.NODE_TYPE_FORK, "validateUser");
        ChainContext ctx = new ChainContext("inst", "chain", Map.of());
        when(lifecycleExecutor.execute(fork, ctx)).thenReturn("FORK_OK");

        NodeResultDTO forkResult = nodeRunner.execute(fork, ctx);
        assertThat(forkResult.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        assertThat(ctx.get("_fork_node_id")).isEqualTo("fk1");

        NodeDefinition join = nodeDef("jn1", ChainConstants.NODE_TYPE_JOIN, "sendNotify");
        when(lifecycleExecutor.execute(join, ctx)).thenReturn("JOIN_OK");
        NodeResultDTO joinResult = nodeRunner.execute(join, ctx);
        assertThat(joinResult.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        assertThat(ctx.get("_join_node_id")).isEqualTo("jn1");
    }

    private static NodeDefinition nodeDef(String id, String type) {
        return nodeDef(id, type, null);
    }

    private static NodeDefinition nodeDef(String id, String type, String component) {
        NodeDefinition.NodeDefinitionBuilder b = NodeDefinition.builder().id(id).type(type).label(id);
        if (component != null) {
            b.component(component);
        }
        return b.build();
    }
}
