package com.zestflow.executor.interceptor;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志拦截器（内置）
 * <p>
 * 在链/节点执行前后打印详细日志。
 */
@Slf4j
public class LoggingInterceptor implements ChainInterceptor, NodeInterceptor {

    @Override
    public void beforeChain(String chainCode, ChainContext ctx) {
        log.info("[CHAIN] 开始执行 chainCode={} instanceId={}", chainCode, ctx.getInstanceId());
    }

    @Override
    public void afterChain(String chainCode, ChainContext ctx, java.util.List<?> nodeResults) {
        log.info("[CHAIN] 执行完成 chainCode={} instanceId={} elapsed={}ms",
                chainCode, ctx.getInstanceId(), System.currentTimeMillis() - ctx.getStartTime());
    }

    @Override
    public void onChainError(String chainCode, ChainContext ctx, Throwable e) {
        log.error("[CHAIN] 执行异常 chainCode={} instanceId={}",
                chainCode, ctx.getInstanceId(), e);
    }

    @Override
    public void beforeNode(NodeDefinition node, ChainContext ctx) {
        log.debug("[NODE] 开始执行 nodeId={} component={}", node.getId(), node.getComponent());
    }

    @Override
    public void afterNode(NodeDefinition node, ChainContext ctx, Object result) {
        log.debug("[NODE] 执行完成 nodeId={}", node.getId());
    }

    @Override
    public void onNodeError(NodeDefinition node, ChainContext ctx, Throwable e) {
        log.error("[NODE] 执行异常 nodeId={} component={}", node.getId(), node.getComponent(), e);
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }
}
