package com.zestflow.executor.retry;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;

/**
 * 重试执行器
 * <p>
 * 根据重试策略执行带重试的节点逻辑。
 */
@Slf4j
public class RetryExecutor {

    private final RetryPolicy retryPolicy;

    public RetryExecutor() {
        this.retryPolicy = new ExponentialBackoffRetryPolicy();
    }

    public RetryExecutor(RetryPolicy retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * 使用重试策略执行
     *
     * @param nodeDef  节点定义
     * @param context  链上下文
     * @param action   要执行的操作（接收 ChainContext，返回是否成功）
     * @return 重试后是否成功
     */
    public boolean executeWithRetry(NodeDefinition nodeDef, ChainContext context,
                                     Function<ChainContext, Object> action) {
        int attempt = 0;
        Throwable lastError;

        do {
            try {
                if (attempt > 0) {
                    long delayMs = retryPolicy.nextDelayMs(nodeDef, attempt - 1);
                    log.debug("节点重试等待 nodeId={} attempt={}/{} delay={}ms",
                            nodeDef.getId(), attempt, nodeDef.getRetryCount(), delayMs);
                    awaitDelay(delayMs, context);
                }

                if (context.isExecutionStopped()) {
                    throw new InterruptedException("链执行已终止");
                }

                action.apply(context);
                return true;

            } catch (Exception e) {
                lastError = e;
                attempt++;
                log.warn("节点重试失败 nodeId={} attempt={}/{} error={}",
                        nodeDef.getId(), attempt, nodeDef.getRetryCount(), e.getMessage());

                if (!retryPolicy.shouldRetry(nodeDef, context, attempt, lastError)) {
                    log.warn("重试耗尽 nodeId={} attempts={}", nodeDef.getId(), attempt);
                    return false;
                }
            }
        } while (true);
    }

    /** 可中断的退避等待（替代 Thread.sleep，便于链取消/线程中断传播） */
    private static void awaitDelay(long delayMs, ChainContext context) throws InterruptedException {
        if (delayMs <= 0) {
            return;
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delayMs);
        while (true) {
            if (context != null && context.isExecutionStopped()) {
                throw new InterruptedException("链执行已终止");
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            LockSupport.parkNanos(remaining);
            if (Thread.interrupted()) {
                throw new InterruptedException("重试等待被中断");
            }
        }
    }
}
