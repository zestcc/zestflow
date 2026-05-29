package com.zestflow.executor.retry;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;

/**
 * 重试策略接口
 * <p>
 * 定义节点执行失败后的重试行为。支持多种策略实现。
 */
public interface RetryPolicy {

    /**
     * 是否应该重试
     *
     * @param nodeDef   节点定义
     * @param context   链上下文
     * @param attempt   已重试次数（从 0 开始）
     * @param lastError 上次错误
     * @return true 表示应继续重试
     */
    boolean shouldRetry(NodeDefinition nodeDef, ChainContext context, int attempt, Throwable lastError);

    /**
     * 获取下次重试前的等待时间（毫秒）
     */
    long nextDelayMs(NodeDefinition nodeDef, int attempt);
}
