package com.zestflow.executor.fallback;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;

/**
 * 降级策略接口
 */
@FunctionalInterface
public interface FallbackStrategy {

    /**
     * 执行降级逻辑
     *
     * @param nodeDef 节点定义
     * @param context 链上下文
     * @param cause   失败原因
     * @return 降级结果
     */
    Object fallback(NodeDefinition nodeDef, ChainContext context, Throwable cause);
}
