package com.zestflow.executor.retry;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ExponentialBackoffRetryPolicyTest {

    private final ExponentialBackoffRetryPolicy policy = new ExponentialBackoffRetryPolicy();

    @Test
    void shouldRetryWithinLimit() {
        NodeDefinition node = NodeDefinition.builder().id("n1").retryCount(3).retryInterval(1000).build();
        ChainContext ctx = new ChainContext("test-instance", "chain1", new HashMap<>());

        assertTrue(policy.shouldRetry(node, ctx, 0, null));
        assertTrue(policy.shouldRetry(node, ctx, 1, null));
        assertTrue(policy.shouldRetry(node, ctx, 2, null));
        assertFalse(policy.shouldRetry(node, ctx, 3, null));
    }

    @Test
    void exponentialDelay() {
        NodeDefinition node = NodeDefinition.builder()
                .id("n1")
                .retryCount(5)
                .retryInterval(1000)
                .retryBackoff(2.0)
                .build();

        // 1000 * 2^0 = 1000
        // 1000 * 2^1 = 2000
        // 1000 * 2^2 = 4000
        // 1000 * 2^3 = 8000
        assertEquals(1000, policy.nextDelayMs(node, 0));
        assertEquals(2000, policy.nextDelayMs(node, 1));
        assertEquals(4000, policy.nextDelayMs(node, 2));
    }
}
