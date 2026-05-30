package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.circuit.SimpleCircuitBreaker;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.lifecycle.NodeStateMachine;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单节点执行器
 * <p>
 * 负责执行节点的完整管线：
 * 拦截器前置 → 元件执行(参数注入+方法调用) → 拦截器后置
 * 异常时：Retry → Fallback（由链定义中的节点配置驱动）
 */
@Slf4j
public class NodeRunner {

    private final ComponentScanner componentScanner;
    private final EventPublisher eventPublisher;
    private final InterceptorChain interceptorChain;
    private final LifecycleExecutor lifecycleExecutor;
    private final RetryExecutor retryExecutor;

    /** 熔断器缓存：nodeId → CircuitBreaker */
    private final ConcurrentHashMap<String, SimpleCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /**
     * 清除指定节点的熔断器状态（链热加载时调用，防止旧熔断状态污染新链）
     */
    public void clearCircuitBreakers(Collection<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) return;
        for (String nodeId : nodeIds) {
            SimpleCircuitBreaker removed = circuitBreakers.remove(nodeId);
            if (removed != null) {
                log.debug("熔断器已清除 nodeId={}", nodeId);
            }
        }
    }

    /** 清除所有熔断器状态 */
    public void clearAllCircuitBreakers() {
        int size = circuitBreakers.size();
        circuitBreakers.clear();
        log.debug("熔断器已全部清除 count={}", size);
    }

    public NodeRunner(ComponentScanner componentScanner, EventPublisher eventPublisher,
                      InterceptorChain interceptorChain, LifecycleExecutor lifecycleExecutor,
                      RetryExecutor retryExecutor) {
        this.componentScanner = componentScanner;
        this.eventPublisher = eventPublisher;
        this.interceptorChain = interceptorChain;
        this.lifecycleExecutor = lifecycleExecutor;
        this.retryExecutor = retryExecutor;
    }

    /**
     * 串行执行单个节点
     */
    public NodeResultDTO execute(NodeDefinition nodeDef, ChainContext context) {
        NodeStateMachine stateMachine = new NodeStateMachine();
        long startTime = System.nanoTime();
        String nodeId = nodeDef.getId();

        try {
            // 熔断器检查
            if (nodeDef.isCircuitBreakerEnabled()) {
                SimpleCircuitBreaker cb = circuitBreakers.computeIfAbsent(nodeId,
                        k -> new SimpleCircuitBreaker(nodeId, nodeDef.getCircuitBreakerThreshold(),
                                nodeDef.getCircuitBreakerRecoveryMs()));
                if (!cb.tryAcquire()) {
                    log.warn("熔断器断开，请求被拒绝 nodeId={}", nodeId);
                    throw new RuntimeException("熔断器已断开 nodeId=" + nodeId);
                }
            }

            stateMachine.transit(ChainConstants.NODE_RUNNING);
            publishNodeEvent(ChainEvent.EventType.NODE_STARTED, nodeId, context);

            // 拦截器前置
            interceptorChain.beforeNode(nodeDef, context);

            // 节点类型分发
            Object result = switch (nodeDef.getType()) {
                case ChainConstants.NODE_TYPE_NORMAL -> executeNormal(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_CONDITION -> executeCondition(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_SCRIPT -> executeScript(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_SUB_CHAIN -> executeSubChain(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_ITERATOR -> executeIterator(nodeDef, context, stateMachine);
                default -> throw new IllegalArgumentException("不支持的节点类型: " + nodeDef.getType());
            };

            // 拦截器后置
            interceptorChain.afterNode(nodeDef, context, result);

            stateMachine.transit(ChainConstants.NODE_SUCCESS);
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeId, context);

            // 熔断器记录成功
            if (nodeDef.isCircuitBreakerEnabled()) {
                SimpleCircuitBreaker cb = circuitBreakers.get(nodeId);
                if (cb != null) cb.onSuccess();
            }

            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.debug("节点执行成功 nodeId={} cost={}ms", nodeId, costMs);

            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(costMs)
                    .outputData(context.snapshot())
                    .build();

        } catch (Exception e) {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.error("节点执行失败 nodeId={} cost={}ms error={}", nodeId, costMs, e.getMessage());

            // 触发重试
            if (nodeDef.getRetryCount() > 0) {
                return handleRetry(nodeDef, context, stateMachine, startTime);
            }

            // 触发降级
            if (nodeDef.getFallbackComponent() != null && !nodeDef.getFallbackComponent().isEmpty()) {
                return handleFallback(nodeDef, context, stateMachine, startTime, e);
            }

            // 真正失败：记录熔断 + 状态转换
            recordCircuitBreakerFailure(nodeDef, nodeId);

            stateMachine.transit(ChainConstants.NODE_FAILED);
            publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeId, context);

            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_FAILED)
                    .costMs(costMs)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private Object executeNormal(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private void executePreProcessors(NodeDefinition nodeDef, ChainContext context) {
        List<ComponentRef> preComponents = nodeDef.getPreComponents();
        if (preComponents == null || preComponents.isEmpty()) return;
        log.debug("前置处理器执行开始 nodeId={} count={}", nodeDef.getId(), preComponents.size());
        lifecycleExecutor.executePreProcessors(preComponents, context);
        log.debug("前置处理器执行完成 nodeId={}", nodeDef.getId());
    }

    private void executePostProcessors(NodeDefinition nodeDef, ChainContext context) {
        List<ComponentRef> postComponents = nodeDef.getPostComponents();
        if (postComponents == null || postComponents.isEmpty()) return;
        log.debug("后置处理器执行开始 nodeId={} count={}", nodeDef.getId(), postComponents.size());
        lifecycleExecutor.executePostProcessors(postComponents, context);
        log.debug("后置处理器执行完成 nodeId={}", nodeDef.getId());
    }

    private Object executeCondition(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        if (nodeDef.getCondition() != null && !nodeDef.getCondition().isEmpty()) {
            boolean satisfied = evaluateCondition(nodeDef.getCondition(), context);
            if (!satisfied) {
                log.debug("条件节点不满足，跳过执行 nodeId={} condition={}", nodeDef.getId(), nodeDef.getCondition());
                stateMachine.transit(ChainConstants.NODE_SKIPPED);
                return null;
            }
        }
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeScript(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        throw new UnsupportedOperationException("脚本节点暂未实现 nodeId=" + nodeDef.getId());
    }

    private Object executeSubChain(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        throw new UnsupportedOperationException("子链节点暂未实现 nodeId=" + nodeDef.getId()
                + " subChainCode=" + nodeDef.getSubChainCode());
    }

    private Object executeIterator(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        throw new UnsupportedOperationException("迭代器节点暂未实现 nodeId=" + nodeDef.getId());
    }

    private NodeResultDTO handleRetry(NodeDefinition nodeDef, ChainContext context,
                                       NodeStateMachine stateMachine, long startTime) {
        publishNodeEvent(ChainEvent.EventType.NODE_RETRYING, nodeDef.getId(), context);

        boolean retried = retryExecutor.executeWithRetry(
                nodeDef, context,
                retryCtx -> lifecycleExecutor.execute(nodeDef, retryCtx)
        );
        long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        if (retried) {
            stateMachine.transit(ChainConstants.NODE_SUCCESS);
            // retry 成功后不会回到 try 块的成功路径，此处补发完成事件
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef.getId(), context);

            if (nodeDef.isCircuitBreakerEnabled()) {
                SimpleCircuitBreaker cb = circuitBreakers.get(nodeDef.getId());
                if (cb != null) cb.onSuccess();
            }

            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(costMs)
                    .build();
        }

        // 重试耗尽，触发降级
        NodeResultDTO fallbackResult = handleFallback(nodeDef, context, stateMachine, startTime, null);

        if (fallbackResult.getStatus() == null || fallbackResult.getStatus() != ChainConstants.NODE_SUCCESS) {
            recordCircuitBreakerFailure(nodeDef, nodeDef.getId());
        }

        return fallbackResult;
    }

    private NodeResultDTO handleFallback(NodeDefinition nodeDef, ChainContext context,
                                          NodeStateMachine stateMachine, long startTime, Throwable cause) {
        publishNodeEvent(ChainEvent.EventType.NODE_FALLBACKING, nodeDef.getId(), context);

        try {
            lifecycleExecutor.executeFallback(nodeDef, context, cause);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            // 降级成功后不会回到 try 块的成功路径，此处补发完成事件
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef.getId(), context);
            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(costMs)
                    .build();
        } catch (Exception fallbackError) {
            log.error("降级执行失败 nodeId={}", nodeDef.getId(), fallbackError);
            stateMachine.transit(ChainConstants.NODE_FAILED);
            publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef.getId(), context);
            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_FAILED)
                    .errorMessage(fallbackError.getMessage())
                    .build();
        }
    }

    private void recordCircuitBreakerFailure(NodeDefinition nodeDef, String nodeId) {
        if (nodeDef.isCircuitBreakerEnabled()) {
            SimpleCircuitBreaker cb = circuitBreakers.get(nodeId);
            if (cb != null) cb.onFailure();
        }
    }

    private void publishNodeEvent(ChainEvent.EventType eventType, String nodeId, ChainContext context) {
        eventPublisher.publish(ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .chainId(context.getInstanceId())
                .chainName(context.getChainCode())
                .nodeId(nodeId)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private boolean evaluateCondition(String condition, ChainContext context) {
        return true;
    }
}
