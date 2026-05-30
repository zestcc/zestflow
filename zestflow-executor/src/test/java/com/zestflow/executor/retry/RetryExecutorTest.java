package com.zestflow.executor.retry;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryExecutorTest {

    @Mock
    private RetryPolicy retryPolicy;

    private RetryExecutor retryExecutor;

    @BeforeEach
    void setUp() {
        retryExecutor = new RetryExecutor(retryPolicy);
    }

    @Test
    void executeWithRetrySucceedsFirstAttempt() {
        NodeDefinition nodeDef = buildNodeDef("node-1", 3);
        ChainContext context = new ChainContext("inst-1", "chain-1", null);

        boolean result = retryExecutor.executeWithRetry(nodeDef, context, ctx -> "success");

        assertThat(result).isTrue();
        verify(retryPolicy, never()).shouldRetry(any(), any(), anyInt(), any());
    }

    @Test
    void executeWithRetrySucceedsAfterRetry() throws Exception {
        NodeDefinition nodeDef = buildNodeDef("node-1", 3);
        ChainContext context = new ChainContext("inst-1", "chain-1", null);

        when(retryPolicy.nextDelayMs(any(), eq(0))).thenReturn(1L);
        when(retryPolicy.shouldRetry(any(), any(), eq(1), any())).thenReturn(true);

        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        boolean result = retryExecutor.executeWithRetry(nodeDef, context, ctx -> {
            if (counter.incrementAndGet() < 2) {
                throw new RuntimeException("临时错误");
            }
            return "success";
        });

        assertThat(result).isTrue();
        assertThat(counter.get()).isEqualTo(2);
        verify(retryPolicy).shouldRetry(any(), any(), eq(1), any());
    }

    @Test
    void executeWithRetryExhaustion() throws Exception {
        NodeDefinition nodeDef = buildNodeDef("node-1", 2);
        ChainContext context = new ChainContext("inst-1", "chain-1", null);

        when(retryPolicy.nextDelayMs(any(), anyInt())).thenReturn(1L);
        when(retryPolicy.shouldRetry(any(), any(), anyInt(), any())).thenReturn(true, false);

        boolean result = retryExecutor.executeWithRetry(nodeDef, context, ctx -> {
            throw new RuntimeException("持续错误");
        });

        assertThat(result).isFalse();
        verify(retryPolicy, atLeastOnce()).shouldRetry(any(), any(), anyInt(), any());
    }

    @Test
    void executeWithRetryPolicyNotCalledOnFirstSuccess() {
        NodeDefinition nodeDef = buildNodeDef("node-1", 3);
        ChainContext context = new ChainContext("inst-1", "chain-1", null);

        retryExecutor.executeWithRetry(nodeDef, context, ctx -> "ok");

        verify(retryPolicy, never()).shouldRetry(any(), any(), anyInt(), any());
        verify(retryPolicy, never()).nextDelayMs(any(), anyInt());
    }

    @Test
    void executeWithRetryZeroRetries() {
        NodeDefinition nodeDef = buildNodeDef("node-1", 0);
        ChainContext context = new ChainContext("inst-1", "chain-1", null);

        boolean result = retryExecutor.executeWithRetry(nodeDef, context, ctx -> {
            throw new RuntimeException("失败");
        });

        assertThat(result).isFalse();
    }

    @Test
    void executeWithRetryDefaultConstructor() {
        RetryExecutor defaultExecutor = new RetryExecutor();
        assertThat(defaultExecutor).isNotNull();
    }

    private NodeDefinition buildNodeDef(String id, int retryCount) {
        return NodeDefinition.builder()
                .id(id)
                .retryCount(retryCount)
                .timeout(-1)
                .build();
    }
}
