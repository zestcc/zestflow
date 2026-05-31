package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.circuit.SimpleCircuitBreaker;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.lifecycle.NodeStateMachine;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.extern.slf4j.Slf4j;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
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
    private final ChainManager chainManager;

    /** 子链执行引擎（setter 注入，避免与 DefaultChainExecutionEngine 循环依赖） */
    private ChainExecutionEngine chainExecutionEngine;

    /** 熔断器缓存：nodeId → CircuitBreaker */
    private final ConcurrentHashMap<String, SimpleCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /** 执行器标识（moduleCode@host:port） */
    private final String executorId;
    /** 应用名 */
    private final String appName;

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
                      RetryExecutor retryExecutor, ChainManager chainManager,
                      com.zestflow.executor.registry.ExecutorProperties properties) {
        this.componentScanner = componentScanner;
        this.eventPublisher = eventPublisher;
        this.interceptorChain = interceptorChain;
        this.lifecycleExecutor = lifecycleExecutor;
        this.retryExecutor = retryExecutor;
        this.chainManager = chainManager;
        this.executorId = properties.getModuleCode() + "@" + properties.getHost() + ":" + properties.getPort();
        this.appName = properties.getModuleName() != null ? properties.getModuleName() : properties.getModuleCode();
    }

    /**
     * 设置子链执行引擎（setter 注入，避免与 DefaultChainExecutionEngine 循环依赖）
     */
    public void setChainExecutionEngine(ChainExecutionEngine chainExecutionEngine) {
        this.chainExecutionEngine = chainExecutionEngine;
    }

    /**
     * 串行执行单个节点
     */
    public NodeResultDTO execute(NodeDefinition nodeDef, ChainContext context) {
        NodeStateMachine stateMachine = new NodeStateMachine();
        long startTime = System.nanoTime();
        String nodeId = nodeDef.getId();

        try {
            // 熔断器检查 — 断开时快速失败，不走重试/降级
            if (nodeDef.isCircuitBreakerEnabled()) {
                SimpleCircuitBreaker cb = circuitBreakers.computeIfAbsent(nodeId,
                        k -> new SimpleCircuitBreaker(nodeId, nodeDef.getCircuitBreakerThreshold(),
                                nodeDef.getCircuitBreakerRecoveryMs()));
                if (!cb.tryAcquire()) {
                    log.warn("熔断器断开，请求被拒绝 nodeId={}", nodeId);
                    stateMachine.transit(ChainConstants.NODE_FAILED);
                    publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef, context, 0L, 0, "熔断器已断开");
                    return NodeResultDTO.builder()
                            .nodeId(nodeId)
                            .status(ChainConstants.NODE_FAILED)
                            .errorMessage("熔断器已断开 nodeId=" + nodeId)
                            .build();
                }
            }

            stateMachine.transit(ChainConstants.NODE_RUNNING);
            publishNodeEvent(ChainEvent.EventType.NODE_STARTED, nodeDef, context, null, null, null);

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
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef, context, costMs, 1, null);
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
            publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef, context, costMs, 0, e.getMessage());

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
        String script = nodeDef.getScript();
        if (script == null || script.isEmpty()) {
            throw new IllegalArgumentException("脚本内容为空 nodeId=" + nodeDef.getId());
        }

        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("groovy");
        if (engine == null) {
            throw new IllegalStateException("Groovy 脚本引擎不可用，请确保 groovy-jsr223 在 classpath 中 nodeId=" + nodeDef.getId());
        }

        Bindings bindings = engine.createBindings();
        bindings.put("ctx", context);
        bindings.put("params", context.snapshot());

        try {
            Object result = engine.eval(script, bindings);
            log.debug("脚本执行成功 nodeId={}", nodeDef.getId());
            return result;
        } catch (ScriptException e) {
            throw new RuntimeException("脚本执行失败 nodeId=" + nodeDef.getId() + " error=" + e.getMessage(), e);
        }
    }

    private Object executeSubChain(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        String subChainCode = nodeDef.getSubChainCode();
        if (subChainCode == null || subChainCode.isEmpty()) {
            throw new IllegalArgumentException("子链编码为空 nodeId=" + nodeDef.getId());
        }

        ChainDefinition subChain = chainManager.get(subChainCode);
        if (subChain == null) {
            throw new IllegalArgumentException("子链不存在 code=" + subChainCode + " nodeId=" + nodeDef.getId());
        }

        if (chainExecutionEngine == null) {
            throw new IllegalStateException("子链执行引擎未注入 nodeId=" + nodeDef.getId());
        }

        log.debug("子链执行开始 nodeId={} subChainCode={}", nodeDef.getId(), subChainCode);
        ChainExecuteResultDTO result = chainExecutionEngine.execute(subChainCode, context.snapshot());
        log.debug("子链执行完成 nodeId={} subChainCode={}", nodeDef.getId(), subChainCode);
        return result.getResultData();
    }

    @SuppressWarnings("unchecked")
    private Object executeIterator(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        String dataSourceExpr = nodeDef.getIteratorDataSource();
        if (dataSourceExpr == null || dataSourceExpr.isEmpty()) {
            log.debug("迭代器数据源表达式为空，跳过 nodeId={}", nodeDef.getId());
            return List.of();
        }

        Object dataSource = context.get(dataSourceExpr);
        if (dataSource == null) {
            log.warn("迭代器数据源为空 nodeId={} expr={}", nodeDef.getId(), dataSourceExpr);
            return List.of();
        }

        if (!(dataSource instanceof Collection)) {
            throw new IllegalArgumentException("迭代器数据源不是集合类型 nodeId=" + nodeDef.getId()
                    + " type=" + dataSource.getClass().getName());
        }

        Collection<Object> items = (Collection<Object>) dataSource;
        String itemName = nodeDef.getIteratorItemName();
        List<NodeDefinition> subNodes = nodeDef.getIteratorSubNodes();

        if (subNodes == null || subNodes.isEmpty()) {
            log.warn("迭代器子节点为空，直接返回数据源 nodeId={}", nodeDef.getId());
            return new ArrayList<>(items);
        }

        List<Object> results = new ArrayList<>();
        int index = 0;
        for (Object item : items) {
            // 将当前迭代项放入上下文
            if (itemName != null && !itemName.isEmpty()) {
                context.put(itemName, item);
            }
            context.put("_iterator_index", index);
            context.put("_iterator_total", items.size());

            // 按序执行所有子节点（同层共享同一个上下文）
            for (NodeDefinition subNode : subNodes) {
                NodeResultDTO subResult = execute(subNode, context);
                if (Objects.equals(subResult.getStatus(), ChainConstants.NODE_FAILED)) {
                    log.warn("迭代器子节点执行失败，中断迭代 nodeId={} subNodeId={} index={}",
                            nodeDef.getId(), subNode.getId(), index);
                    return results;
                }
            }
            index++;
        }

        log.debug("迭代器执行完成 nodeId={} count={}", nodeDef.getId(), index);
        return results;
    }

    private NodeResultDTO handleRetry(NodeDefinition nodeDef, ChainContext context,
                                       NodeStateMachine stateMachine, long startTime) {
        publishNodeEvent(ChainEvent.EventType.NODE_RETRYING, nodeDef, context, null, null, null);

        boolean retried = retryExecutor.executeWithRetry(
                nodeDef, context,
                retryCtx -> lifecycleExecutor.execute(nodeDef, retryCtx)
        );
        long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        if (retried) {
            stateMachine.transit(ChainConstants.NODE_SUCCESS);
            // retry 成功后不会回到 try 块的成功路径，此处补发完成事件
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef, context, costMs, 1, null);

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
        publishNodeEvent(ChainEvent.EventType.NODE_FALLBACK_START, nodeDef, context, null, null, null);
        long costMs = 0;

        try {
            lifecycleExecutor.executeFallback(nodeDef, context, cause);
            costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            // 降级成功后不会回到 try 块的成功路径，此处补发完成事件
            publishNodeEvent(ChainEvent.EventType.NODE_FALLBACK_SUCCESS, nodeDef, context, costMs, 1, null);
            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(costMs)
                    .build();
        } catch (Exception fallbackError) {
            log.error("降级执行失败 nodeId={}", nodeDef.getId(), fallbackError);
            stateMachine.transit(ChainConstants.NODE_FAILED);
            publishNodeEvent(ChainEvent.EventType.NODE_FALLBACK_FAILED, nodeDef, context, costMs, 0, fallbackError.getMessage());
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

    private void publishNodeEvent(ChainEvent.EventType eventType, NodeDefinition nodeDef,
                                   ChainContext context, Long costMs, Integer status, String errorMessage) {
        eventPublisher.publish(ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .executionId(context.getInstanceId())
                .chainId(context.getInstanceId())
                .chainName(context.getChainCode())
                .nodeId(nodeDef.getId())
                .nodeName(nodeDef.getLabel())
                .executorId(executorId)
                .appName(appName)
                .costMs(costMs)
                .status(status)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private boolean evaluateCondition(String condition, ChainContext context) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        try {
            String expr = condition.trim();
            if (expr.startsWith("${") && expr.endsWith("}")) {
                expr = expr.substring(2, expr.length() - 1);
            }
            ScriptEngine engine = new ScriptEngineManager().getEngineByName("groovy");
            if (engine == null) {
                log.warn("Groovy 引擎不可用，条件表达式视为 true condition={}", condition);
                return true;
            }
            Bindings bindings = engine.createBindings();
            Map<String, Object> snapshot = context.snapshot();
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                bindings.put(entry.getKey(), entry.getValue());
            }
            Object result = engine.eval(expr, bindings);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("条件表达式评估失败 condition={}", condition, e);
            return false;
        }
    }
}
