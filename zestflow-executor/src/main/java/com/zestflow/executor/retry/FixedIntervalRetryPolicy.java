package com.zestflow.executor.retry;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;

/**
 * 固定间隔重试策略
 */
public class FixedIntervalRetryPolicy implements RetryPolicy {

    @Override
    public boolean shouldRetry(NodeDefinition nodeDef, ChainContext context, int attempt, Throwable lastError) {
        return attempt < nodeDef.getRetryCount();
    }

    @Override
    public long nextDelayMs(NodeDefinition nodeDef, int attempt) {
        return nodeDef.getRetryInterval();
    }
}
