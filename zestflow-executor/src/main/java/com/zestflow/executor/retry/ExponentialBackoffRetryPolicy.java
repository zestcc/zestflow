package com.zestflow.executor.retry;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;

/**
 * 指数退避重试策略
 * <p>
 * 重试间隔按退避因子递增：interval * backoff^attempt
 * 最小 1s，最大 60s
 */
public class ExponentialBackoffRetryPolicy implements RetryPolicy {

    private static final long MIN_DELAY_MS = 1000L;
    private static final long MAX_DELAY_MS = 60_000L;

    @Override
    public boolean shouldRetry(NodeDefinition nodeDef, ChainContext context, int attempt, Throwable lastError) {
        return attempt < nodeDef.getRetryCount();
    }

    @Override
    public long nextDelayMs(NodeDefinition nodeDef, int attempt) {
        double backoff = nodeDef.getRetryBackoff() > 0 ? nodeDef.getRetryBackoff() : 2.0;
        long delay = (long) (nodeDef.getRetryInterval() * Math.pow(backoff, attempt));
        return Math.min(Math.max(delay, MIN_DELAY_MS), MAX_DELAY_MS);
    }
}
