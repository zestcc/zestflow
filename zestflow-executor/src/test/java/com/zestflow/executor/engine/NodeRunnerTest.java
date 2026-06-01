package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.common.spi.EventCollector;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeRunnerTest {

    @Mock ComponentScanner componentScanner;
    @Mock EventCollector eventCollector;
    @Mock InterceptorChain interceptorChain;
    @Mock LifecycleExecutor lifecycleExecutor;
    @Mock RetryExecutor retryExecutor;
    @Mock ChainManager chainManager;
    @Mock com.zestflow.executor.registry.ExecutorProperties executorProperties;

    @Captor ArgumentCaptor<ChainEvent> eventCaptor;

    private String executorId;

    private NodeRunner nodeRunner;

    @BeforeEach
    void setUp() {
        when(executorProperties.getAppCode()).thenReturn("test-app");
        when(executorProperties.getHost()).thenReturn("127.0.0.1");
        when(executorProperties.getPort()).thenReturn(9999);
        when(executorProperties.getAppName()).thenReturn("test-app");
        executorId = "test-app@127.0.0.1:9999";
        nodeRunner = new NodeRunner(componentScanner, eventCollector,
                interceptorChain, lifecycleExecutor, retryExecutor, chainManager, executorProperties);
    }

    // ==================== NodeStateMachine 单元测试 ====================

    @Test
    void normalExecutionSucceeds() {
        NodeDefinition nodeDef = nodeDef("n1", ChainConstants.NODE_TYPE_NORMAL);
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        assertThat(result.getNodeId()).isEqualTo("n1");
        assertThat(result.getOutputData()).isNotNull();
        verify(interceptorChain).beforeNode(nodeDef, ctx);
        verify(interceptorChain).afterNode(eq(nodeDef), eq(ctx), any());
        verify(eventCollector, atLeast(2)).collect(any(ChainEvent.class));
    }

    @Test
    void normalExecutionWithoutPrePostDoesNotCallProcessors() {
        NodeDefinition nodeDef = nodeDef("n1", ChainConstants.NODE_TYPE_NORMAL);
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        nodeRunner.execute(nodeDef, ctx);

        verify(lifecycleExecutor, never()).executePreProcessors(any(), any());
        verify(lifecycleExecutor, never()).executePostProcessors(any(), any());
    }

    @Test
    void normalExecutionWithPrePostProcessors() {
        ComponentRef pre1 = new ComponentRef("pre1", "前处理1");
        ComponentRef post1 = new ComponentRef("post1", "后处理1");
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .preComponents(List.of(pre1))
                .postComponents(List.of(post1))
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        nodeRunner.execute(nodeDef, ctx);

        verify(lifecycleExecutor).executePreProcessors(List.of(pre1), ctx);
        verify(lifecycleExecutor).execute(nodeDef, ctx);
        verify(lifecycleExecutor).executePostProcessors(List.of(post1), ctx);
    }

    @Test
    void normalExecutionWithNullPrePostIsNoOp() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .preComponents(null).postComponents(null).build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        nodeRunner.execute(nodeDef, ctx);

        verify(lifecycleExecutor, never()).executePreProcessors(any(), any());
        verify(lifecycleExecutor, never()).executePostProcessors(any(), any());
    }

    @Test
    void normalExecutionWithEmptyPrePostIsNoOp() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .preComponents(List.of()).postComponents(List.of()).build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        nodeRunner.execute(nodeDef, ctx);

        verify(lifecycleExecutor, never()).executePreProcessors(any(), any());
        verify(lifecycleExecutor, never()).executePostProcessors(any(), any());
    }

    @Test
    void preProcessorFailurePropagatesToCatchBlock() {
        ComponentRef pre1 = new ComponentRef("pre1", "会失败的前置");
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .preComponents(List.of(pre1))
                .build();
        ChainContext ctx = context();

        doThrow(new RuntimeException("pre-processor failed"))
                .when(lifecycleExecutor).executePreProcessors(any(), any());

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        verify(lifecycleExecutor, never()).execute(any(), any());
        verify(lifecycleExecutor, never()).executePostProcessors(any(), any());
    }

    // ==================== 条件节点 ====================

    @Test
    void conditionSatisfiedExecutesMainWithPrePost() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_CONDITION).component("main")
                .condition("true")
                .preComponents(List.of(new ComponentRef("pre1", null)))
                .postComponents(List.of(new ComponentRef("post1", null)))
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        verify(lifecycleExecutor).executePreProcessors(any(), eq(ctx));
        verify(lifecycleExecutor).execute(nodeDef, ctx);
        verify(lifecycleExecutor).executePostProcessors(any(), eq(ctx));
    }

    @Test
    void conditionWithNullConditionExecutesMain() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_CONDITION).component("main")
                .condition(null)
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    // ==================== 重试 ====================

    @Test
    void retrySuccessAfterMainFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .retryCount(3).retryInterval(10)
                .build();
        ChainContext ctx = context();

        when(lifecycleExecutor.execute(any(), any()))
                .thenThrow(new RuntimeException("first fail"))
                .thenReturn("success");

        when(retryExecutor.executeWithRetry(any(), any(), any())).thenAnswer(invocation -> {
            Function<ChainContext, Object> action = invocation.getArgument(2);
            action.apply(ctx);
            return true;
        });

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        verify(retryExecutor).executeWithRetry(any(), any(), any());
    }

    @Test
    void retryExhaustedThenFallbackSuccess() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .retryCount(3).retryInterval(10)
                .fallbackComponent("fallbackComp")
                .build();
        ChainContext ctx = context();

        when(lifecycleExecutor.execute(nodeDef, ctx))
                .thenThrow(new RuntimeException("always fail"));

        when(retryExecutor.executeWithRetry(any(), any(), any())).thenAnswer(invocation -> {
            Function<ChainContext, Object> action = invocation.getArgument(2);
            try { action.apply(ctx); } catch (Exception ignored) { }
            return false;
        });

        when(lifecycleExecutor.executeFallback(any(), any(), any())).thenReturn("fallback ok");

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
        verify(retryExecutor).executeWithRetry(any(), any(), any());
        verify(lifecycleExecutor).executeFallback(any(), any(), any());
    }

    @Test
    void retryExhaustedThenFallbackFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .retryCount(3).retryInterval(10)
                .fallbackComponent("fallbackComp")
                .build();
        ChainContext ctx = context();

        when(lifecycleExecutor.execute(nodeDef, ctx))
                .thenThrow(new RuntimeException("always fail"));

        when(retryExecutor.executeWithRetry(any(), any(), any())).thenAnswer(invocation -> {
            Function<ChainContext, Object> action = invocation.getArgument(2);
            try { action.apply(ctx); } catch (Exception ignored) { }
            return false;
        });

        when(lifecycleExecutor.executeFallback(any(), any(), any()))
                .thenThrow(new RuntimeException("fallback also failed"));

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("fallback also failed");
    }

    @Test
    void noRetryAndNoFallbackReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .retryCount(0).fallbackComponent(null)
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx))
                .thenThrow(new RuntimeException("main failed"));

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("main failed");
    }

    // ==================== 熔断器 ====================

    @Test
    void circuitBreakerEnabledFailureOpensBreaker() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .circuitBreakerEnabled(true)
                .circuitBreakerThreshold(1)
                .circuitBreakerRecoveryMs(60000)
                .build();
        ChainContext ctx = context();

        when(lifecycleExecutor.execute(any(), any()))
                .thenThrow(new RuntimeException("fail"));

        // First call: fails, opens breaker
        NodeResultDTO first = nodeRunner.execute(nodeDef, ctx);
        assertThat(first.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);

        // Second call: circuit breaker open → rejection
        NodeResultDTO second = nodeRunner.execute(nodeDef, ctx);
        assertThat(second.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(second.getErrorMessage()).contains("熔断器已断开");

        // Third call: still rejected
        NodeResultDTO third = nodeRunner.execute(nodeDef, ctx);
        assertThat(third.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(third.getErrorMessage()).contains("熔断器已断开");
    }

    @Test
    void circuitBreakerRecoversAfterSuccess() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .circuitBreakerEnabled(true)
                .circuitBreakerThreshold(1)
                .circuitBreakerRecoveryMs(50)
                .build();
        ChainContext ctx = context();

        when(lifecycleExecutor.execute(any(), any()))
                .thenThrow(new RuntimeException("fail")) // 1st call: fail
                .thenReturn("success");                  // 2nd+ calls: success

        // First call: fails, opens breaker
        nodeRunner.execute(nodeDef, ctx);

        // Wait for recovery window
        sleepUninterruptibly(60);

        // Second call: recovery window passed → breaker half-open → success → closed
        NodeResultDTO second = nodeRunner.execute(nodeDef, ctx);
        assertThat(second.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void circuitBreakerDisabledDoesNotTrackFailures() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .circuitBreakerEnabled(false)
                .build();
        ChainContext ctx = context();

        when(lifecycleExecutor.execute(any(), any()))
                .thenThrow(new RuntimeException("fail")); // always fails

        // Multiple failures should all fail normally (no rejection from breaker)
        for (int i = 0; i < 5; i++) {
            NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);
            assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
            assertThat(result.getErrorMessage()).contains("fail");
        }
    }

    // ==================== 事件发布 ====================

    @Test
    void publishesNodeStartedAndCompletedEvents() {
        NodeDefinition nodeDef = nodeDef("n1", ChainConstants.NODE_TYPE_NORMAL);
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx)).thenReturn("ok");

        nodeRunner.execute(nodeDef, ctx);

        verify(eventCollector, atLeast(2)).collect(eventCaptor.capture());
        List<ChainEvent.EventType> types = eventCaptor.getAllValues().stream()
                .map(ChainEvent::getEventType).toList();
        assertThat(types).contains(
                ChainEvent.EventType.NODE_STARTED,
                ChainEvent.EventType.NODE_COMPLETED
        );
    }

    @Test
    void publishesNodeFailedEventOnError() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .retryCount(0).fallbackComponent(null)
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(nodeDef, ctx))
                .thenThrow(new RuntimeException("fail"));

        nodeRunner.execute(nodeDef, ctx);

        verify(eventCollector, atLeast(2)).collect(eventCaptor.capture());
        List<ChainEvent.EventType> types = eventCaptor.getAllValues().stream()
                .map(ChainEvent::getEventType).toList();
        assertThat(types).contains(
                ChainEvent.EventType.NODE_STARTED,
                ChainEvent.EventType.NODE_FAILED
        );
    }

    // ==================== 脚本节点 ====================

    @Test
    void scriptNodeEmptyContentReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_SCRIPT)
                .script("")
                .build();
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("脚本内容为空");
    }

    @Test
    void scriptNodeNullContentReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_SCRIPT)
                .script(null)
                .build();
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("脚本内容为空");
    }

    // ==================== 子链节点 ====================

    @Test
    void subChainNodeEmptyCodeReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_SUB_CHAIN)
                .subChainCode("")
                .build();
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("子链编码为空");
    }

    @Test
    void subChainNodeNullCodeReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_SUB_CHAIN)
                .subChainCode(null)
                .build();
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("子链编码为空");
    }

    @Test
    void subChainNodeNotFoundReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_SUB_CHAIN)
                .subChainCode("non-existent-chain")
                .build();
        ChainContext ctx = context();

        when(chainManager.get("non-existent-chain")).thenReturn(null);

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("子链不存在");
    }

    // ==================== 迭代器节点 ====================

    @Test
    void iteratorNodeEmptyDataSourceReturnsSuccess() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_ITERATOR)
                .iteratorDataSource("")
                .build();
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void iteratorNodeNullDataSourceReturnsSuccess() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_ITERATOR)
                .iteratorDataSource(null)
                .build();
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void iteratorNodeNullContextValueReturnsSuccess() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_ITERATOR)
                .iteratorDataSource("items")
                .build();
        ChainContext ctx = new ChainContext("test-instance", "test-chain", null);

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_SUCCESS);
    }

    @Test
    void iteratorNodeNonCollectionDataSourceReturnsFailure() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").type(ChainConstants.NODE_TYPE_ITERATOR)
                .iteratorDataSource("items")
                .build();
        ChainContext ctx = new ChainContext("test-instance", "test-chain", Map.of("items", "not-a-collection"));

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("不是集合类型");
    }

    // ==================== 不支持节点类型 ====================

    @Test
    void unsupportedNodeTypeThrows() {
        NodeDefinition nodeDef = nodeDef("n1", "UNKNOWN_TYPE");
        ChainContext ctx = context();

        NodeResultDTO result = nodeRunner.execute(nodeDef, ctx);

        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("不支持的节点类型");
    }

    // ==================== 熔断器清理 ====================

    @Test
    void clearCircuitBreakersRemovesSpecifiedNodeIds() {
        NodeDefinition node1 = NodeDefinition.builder()
                .id("breaker-n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .circuitBreakerEnabled(true).circuitBreakerThreshold(1).circuitBreakerRecoveryMs(60000)
                .build();
        NodeDefinition node2 = NodeDefinition.builder()
                .id("breaker-n2").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .circuitBreakerEnabled(true).circuitBreakerThreshold(1).circuitBreakerRecoveryMs(60000)
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(any(NodeDefinition.class), any(ChainContext.class)))
                .thenThrow(new RuntimeException("fail"));

        // Trigger circuit breaker for both nodes
        nodeRunner.execute(node1, ctx);
        nodeRunner.execute(node2, ctx);

        // Clear only node1
        nodeRunner.clearCircuitBreakers(Set.of("breaker-n1"));

        // node1 should execute again (breaker was cleared), and fail with "fail"
        NodeResultDTO n1Result = nodeRunner.execute(node1, ctx);
        assertThat(n1Result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);

        // node2 should still be rejected by circuit breaker
        NodeResultDTO n2Result = nodeRunner.execute(node2, ctx);
        assertThat(n2Result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(n2Result.getErrorMessage()).contains("熔断器已断开");
    }

    @Test
    void clearCircuitBreakersWithNullOrEmptyIsNoOp() {
        // Should not throw
        nodeRunner.clearCircuitBreakers(null);
        nodeRunner.clearCircuitBreakers(Set.of());
    }

    @Test
    void clearAllCircuitBreakersClearsAll() {
        NodeDefinition node1 = NodeDefinition.builder()
                .id("breaker-n1").type(ChainConstants.NODE_TYPE_NORMAL).component("main")
                .circuitBreakerEnabled(true).circuitBreakerThreshold(1).circuitBreakerRecoveryMs(60000)
                .build();
        ChainContext ctx = context();
        when(lifecycleExecutor.execute(any(), any())).thenThrow(new RuntimeException("fail"));

        nodeRunner.execute(node1, ctx);
        nodeRunner.clearAllCircuitBreakers();

        // Breaker should be reset, so execution proceeds (and fails normally)
        NodeResultDTO result = nodeRunner.execute(node1, ctx);
        assertThat(result.getStatus()).isEqualTo(ChainConstants.NODE_FAILED);
        assertThat(result.getErrorMessage()).contains("fail");
    }

    // ==================== 辅助方法 ====================

    private static NodeDefinition nodeDef(String id, String type) {
        return NodeDefinition.builder().id(id).type(type).build();
    }

    private static ChainContext context() {
        return new ChainContext("test-instance", "test-chain", Map.of("key", "val"));
    }

    private static void sleepUninterruptibly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
