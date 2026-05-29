package com.zestflow.executor.fallback;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

/**
 * 默认降级策略：记录日志后返回 null
 */
@Slf4j
public class DefaultFallbackStrategy implements FallbackStrategy {

    @Override
    public Object fallback(NodeDefinition nodeDef, ChainContext context, Throwable cause) {
        log.warn("节点执行降级 nodeId={} component={} error={}",
                nodeDef.getId(), nodeDef.getComponent(), cause != null ? cause.getMessage() : "unknown");
        return null;
    }
}
