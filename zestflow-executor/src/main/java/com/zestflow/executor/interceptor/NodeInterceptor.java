package com.zestflow.executor.interceptor;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;

/**
 * 节点级拦截器接口
 * <p>
 * 在每个节点执行前/后/异常时触发。
 */
public interface NodeInterceptor {

    /**
     * 节点执行前
     */
    void beforeNode(NodeDefinition node, ChainContext ctx);

    /**
     * 节点执行后
     */
    void afterNode(NodeDefinition node, ChainContext ctx, Object result);

    /**
     * 节点异常
     */
    void onNodeError(NodeDefinition node, ChainContext ctx, Throwable e);

    /**
     * 排序
     */
    default int order() {
        return 0;
    }
}
