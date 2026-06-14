package com.zestflow.executor.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.circuit.SimpleCircuitBreaker;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.LifecycleExecutor;
import com.zestflow.executor.lifecycle.NodeStateMachine;
import com.zestflow.executor.retry.RetryExecutor;
import com.zestflow.executor.scanner.ComponentScanner;
import com.zestflow.executor.scanner.ComponentScanner.ComponentMeta;
import com.zestflow.executor.scanner.ComponentScanner.TagDef;
import lombok.extern.slf4j.Slf4j;

import com.zestflow.executor.expression.AviatorExpressionEvaluator;
import com.zestflow.executor.expression.ExpressionEvaluationException;
import com.zestflow.executor.context.ExecuteResultPublisher;
import com.zestflow.executor.fallback.FallbackStrategy;
import com.zestflow.executor.http.NativeHttpClient;
import com.zestflow.executor.http.NodeConfigBridge;
import com.zestflow.executor.security.SensitiveDataMasker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
    private final FallbackStrategy fallbackStrategy;

    /** 子链执行引擎（setter 注入，避免与 DefaultChainExecutionEngine 循环依赖） */
    private ChainExecutionEngine chainExecutionEngine;

    /** 熔断器缓存：nodeId → CircuitBreaker */
    private final ConcurrentHashMap<String, SimpleCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    /** 执行器标识（appCode@host:port） */
    private final String executorId;
    /** 应用编码 */
    private final String appCode;
    /** 应用名 */
    private final String appName;
    /** 租户 ID */
    private final long tenantId;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
                      com.zestflow.executor.registry.ExecutorProperties properties,
                      FallbackStrategy fallbackStrategy) {
        this.componentScanner = componentScanner;
        this.eventPublisher = eventPublisher != null ? eventPublisher : EventPublisher.noop();
        this.interceptorChain = interceptorChain;
        this.lifecycleExecutor = lifecycleExecutor;
        this.retryExecutor = retryExecutor;
        this.chainManager = chainManager;
        this.fallbackStrategy = fallbackStrategy != null ? fallbackStrategy : new com.zestflow.executor.fallback.DefaultFallbackStrategy();
        this.executorId = properties.getAppCode() + "@" + properties.getHost() + ":" + properties.getPort();
        this.appCode = properties.getAppCode();
        this.appName = properties.getAppName() != null ? properties.getAppName() : properties.getAppCode();
        this.tenantId = properties.getTenantId();
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
        if (context.isExecutionStopped()) {
            return stoppedResult(nodeDef.getId());
        }

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
                    publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef, context, 0L, 0, "熔断器已断开", null, null);
                    return NodeResultDTO.builder()
                            .nodeId(nodeId)
                            .status(ChainConstants.NODE_FAILED)
                            .errorMessage("熔断器已断开 nodeId=" + nodeId)
                            .build();
                }
            }

            stateMachine.transit(ChainConstants.NODE_READY);
            stateMachine.transit(ChainConstants.NODE_RUNNING);
            publishNodeEvent(ChainEvent.EventType.NODE_STARTED, nodeDef, context, null, null, null,
                    toJsonString(context.snapshot()), null);

            // 拦截器前置
            interceptorChain.beforeNode(nodeDef, context);

            applyNodeConfigToContext(nodeDef, context);

            // 节点类型分发
            Object result = switch (nodeDef.getType()) {
                case ChainConstants.NODE_TYPE_NORMAL -> executeNormal(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_CONDITION -> executeCondition(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_SELECTOR -> executeSelector(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_SCRIPT -> executeScript(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_SUB_CHAIN -> executeSubChain(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_ITERATOR -> executeIterator(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_FORK -> executeFork(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_JOIN -> executeJoin(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_TRY_CATCH -> executeTryCatch(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_WHILE -> executeWhile(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_APPROVAL -> executeApproval(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_NOTIFICATION -> executeNotification(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_TRANSFORMER -> executeTransformer(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_FILTER -> executeFilter(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_AGGREGATOR -> executeAggregator(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_SPLITTER -> executeSplitter(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_HTTP_CLIENT -> executeHttpClient(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_MQ_PRODUCER -> executeMqProducer(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_MQ_CONSUMER -> executeMqConsumer(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_CACHE_READER -> executeCacheReader(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_CACHE_WRITER -> executeCacheWriter(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_LOGGER -> executeLogger(nodeDef, context, stateMachine);
                case ChainConstants.NODE_TYPE_DELAY -> executeDelay(nodeDef, context, stateMachine);
                default -> throw new IllegalArgumentException("不支持的节点类型: " + nodeDef.getType());
            };

            // 拦截器后置
            interceptorChain.afterNode(nodeDef, context, result);

            if (ChainConstants.NODE_TYPE_APPROVAL.equals(nodeDef.getType())
                    && "PENDING_APPROVAL".equals(result)) {
                stateMachine.transit(ChainConstants.NODE_SKIPPED);
                long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef, context, costMs, 1, null,
                        null, toJsonString(result));
                return NodeResultDTO.builder()
                        .nodeId(nodeId)
                        .status(ChainConstants.NODE_SKIPPED)
                        .costMs(costMs)
                        .returnValue(result)
                        .outputData(context.snapshot())
                        .build();
            }

            stateMachine.transit(ChainConstants.NODE_SUCCESS);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef, context, costMs, 1, null,
                    null, toJsonString(result));
            log.debug("节点执行成功 nodeId={} cost={}ms", nodeId, costMs);

            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(costMs)
                    .returnValue(result)
                    .outputData(context.snapshot())
                    .build();

        } catch (Exception e) {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.error("节点执行失败 nodeId={} cost={}ms error={}", nodeId, costMs, e.getMessage(), e);

            // 触发重试
            if (nodeDef.getRetryCount() > 0) {
                return handleRetry(nodeDef, context, stateMachine, startTime);
            }

            // 触发降级
            if (hasFallbackConfigured(nodeDef)) {
                return handleFallback(nodeDef, context, stateMachine, startTime, e);
            }

            // 真正失败：记录熔断 + 状态转换
            recordCircuitBreakerFailure(nodeDef, nodeId);

            stateMachine.transit(ChainConstants.NODE_FAILED);
            publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef, context, costMs, 0, e.getMessage(), null, null);

            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_FAILED)
                    .costMs(costMs)
                    .errorMessage(e.getMessage())
                    .errorCode(resolveErrorCode(e))
                    .build();
        }
    }

    private static String resolveErrorCode(Throwable e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof BizException biz) {
                return biz.getErrorCode();
            }
            String reflected = reflectErrorCode(t);
            if (reflected != null) {
                return reflected;
            }
            t = t.getCause();
        }
        return null;
    }

    private static String reflectErrorCode(Throwable t) {
        try {
            var method = t.getClass().getMethod("getErrorCode");
            Object value = method.invoke(t);
            if (value != null) {
                String code = String.valueOf(value).trim();
                if (!code.isEmpty()) {
                    return code;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // 非业务异常类型
        }
        return null;
    }

    /**
     * 补偿已成功的节点（COMPENSATE 策略，逆序调用）。
     */
    public NodeResultDTO compensate(NodeDefinition nodeDef, ChainContext context) {
        String nodeId = nodeDef.getId();
        long startTime = System.nanoTime();
        String compensateId = LifecycleExecutor.resolveCompensateComponentId(nodeDef);

        if (compensateId == null || compensateId.isEmpty()
                || componentScanner.getComponent(compensateId) == null) {
            log.debug("节点无可用补偿元件，跳过 nodeId={}", nodeId);
            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_COMPENSATED)
                    .costMs(0L)
                    .build();
        }

        if (context.isExecutionStopped()) {
            return stoppedResult(nodeId);
        }

        try {
            publishNodeEvent(ChainEvent.EventType.NODE_COMPENSATING, nodeDef, context,
                    null, null, null, null, null);
            Object result = lifecycleExecutor.executeCompensate(nodeDef, context);
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            publishNodeEvent(ChainEvent.EventType.NODE_COMPENSATED, nodeDef, context, costMs, 1,
                    null, null, toJsonString(result));
            log.debug("节点补偿完成 nodeId={} cost={}ms", nodeId, costMs);
            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_COMPENSATED)
                    .costMs(costMs)
                    .build();
        } catch (Exception e) {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            log.error("节点补偿失败 nodeId={} error={}", nodeId, e.getMessage(), e);
            publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef, context, costMs, 0,
                    e.getMessage(), null, null);
            return NodeResultDTO.builder()
                    .nodeId(nodeId)
                    .status(ChainConstants.NODE_FAILED)
                    .costMs(costMs)
                    .errorMessage("补偿失败: " + e.getMessage())
                    .build();
        }
    }

    private static NodeResultDTO stoppedResult(String nodeId) {
        return NodeResultDTO.builder()
                .nodeId(nodeId)
                .status(ChainConstants.NODE_FAILED)
                .errorMessage("链执行已终止")
                .build();
    }

    private Object executeNormal(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private void publishResult(ChainContext context, Object result, NodeDefinition nodeDef) {
        String outputKey = null;
        String componentId = nodeDef.getComponent();
        if (componentId != null && !componentId.isEmpty()) {
            ComponentMeta meta = componentScanner.getComponent(componentId);
            if (meta != null) {
                outputKey = meta.getOutputKey();
            }
        }
        ExecuteResultPublisher.publish(context, result, outputKey);
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

        // 内联脚本判断：表达式随设计持久化，不依赖 @ZestPredicate 元件
        if (nodeDef.isInlineScriptPredicate()) {
            return executeInlineScriptPredicate(nodeDef, context, stateMachine);
        }

        // 无 component 的 CONDITION 节点是纯路由器，跳过元件执行
        String componentId = nodeDef.getComponent();
        if (componentId == null || componentId.isEmpty()) {
            log.debug("条件节点为纯路由器，跳过元件执行 nodeId={}", nodeDef.getId());
            return null;
        }

        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);

        // 将路由决策写入上下文，供引擎选择匹配的出边
        if (result != null) {
            String branchLabel = resolveBranchLabel(nodeDef, result.toString());
            if (branchLabel != null) {
                context.put("_branch", branchLabel);
                log.debug("CONDITION 节点路由决策 nodeId={} result={} branch={}",
                        nodeDef.getId(), result, branchLabel);
            }
        }

        return result;
    }

    /**
     * 内联脚本判断：评估 Aviator 表达式，将 True/False 标签写入 _branch 供引擎路由。
     */
    private Object executeInlineScriptPredicate(NodeDefinition nodeDef, ChainContext context,
                                                NodeStateMachine stateMachine) {
        String script = nodeDef.getPredicateScript();
        if (script == null || script.isBlank()) {
            throw new IllegalArgumentException("脚本判断表达式为空 nodeId=" + nodeDef.getId());
        }

        executePreProcessors(nodeDef, context);
        boolean matched = evaluateCondition(script, context);
        String trueLabel = nodeDef.getTrueLabel() != null && !nodeDef.getTrueLabel().isBlank()
                ? nodeDef.getTrueLabel() : "True";
        String falseLabel = nodeDef.getFalseLabel() != null && !nodeDef.getFalseLabel().isBlank()
                ? nodeDef.getFalseLabel() : "False";
        String branch = matched ? trueLabel : falseLabel;
        context.put("_branch", branch);
        executePostProcessors(nodeDef, context);

        log.debug("CONDITION 脚本判断 nodeId={} component={} matched={} branch={}",
                nodeDef.getId(), nodeDef.getComponent(), matched, branch);
        return branch;
    }

    /**
     * 解析 CONDITION 节点的路由分支标签
     * <p>
     * 优先匹配 @ZestTag.value → @ZestTag.name；无 TagDef 时直接用返回值。
     */
    private String resolveBranchLabel(NodeDefinition nodeDef, String resultStr) {
        String componentId = nodeDef.getComponent();
        if (componentId == null || componentId.isEmpty()) {
            return resultStr;
        }
        ComponentMeta meta = componentScanner.getComponent(componentId);
        if (meta == null || meta.getTagDefs() == null || meta.getTagDefs().isEmpty()) {
            return resultStr;
        }
        for (TagDef tag : meta.getTagDefs()) {
            if (tag.getValue().equals(resultStr)) {
                return tag.getName();
            }
        }
        log.warn("CONDITION 节点返回值未匹配任何 @ZestTag nodeId={} component={} result={} tags={}",
                nodeDef.getId(), componentId, resultStr,
                meta.getTagDefs().stream().map(t -> t.getName() + "=" + t.getValue()).toList());
        return null;
    }

    private Object executeScript(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        String script = normalizeScriptContent(nodeDef.getScript());
        if (script == null || script.isEmpty()) {
            throw new IllegalArgumentException("脚本内容为空 nodeId=" + nodeDef.getId());
        }

        try {
            Object result = AviatorExpressionEvaluator.execute(script, AviatorExpressionEvaluator.buildEnv(context));
            log.debug("脚本执行成功 nodeId={}", nodeDef.getId());
            return result;
        } catch (ExpressionEvaluationException e) {
            throw new RuntimeException("脚本执行失败 nodeId=" + nodeDef.getId() + " error=" + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("脚本执行失败 nodeId=" + nodeDef.getId() + " error=" + e.getMessage(), e);
        }
    }

    /** 兼容设计器/种子数据中未解码的 \\u0027 字面量 */
    static String normalizeScriptContent(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        return script.replace("\\u0027", "'");
    }

    private Object executeSubChain(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        String subChainCode = nodeDef.getSubChainCode();
        if (subChainCode == null || subChainCode.isEmpty()) {
            throw new IllegalArgumentException("子链编码为空 nodeId=" + nodeDef.getId());
        }

        // 递归深度检查
        int currentDepth = getSubChainDepth(context);
        if (currentDepth >= ChainConstants.MAX_SUB_CHAIN_DEPTH) {
            throw new RuntimeException("子链递归深度超限 nodeId=" + nodeDef.getId()
                    + " depth=" + currentDepth + " max=" + ChainConstants.MAX_SUB_CHAIN_DEPTH);
        }

        ChainDefinition subChain = chainManager.get(subChainCode);
        if (subChain == null) {
            throw new IllegalArgumentException("子链不存在 code=" + subChainCode + " nodeId=" + nodeDef.getId());
        }

        if (chainExecutionEngine == null) {
            throw new IllegalStateException("子链执行引擎未注入 nodeId=" + nodeDef.getId());
        }

        log.debug("子链执行开始 nodeId={} subChainCode={} depth={} parentDeadlineMs={}",
                nodeDef.getId(), subChainCode, currentDepth, readDeadlineMs(context));
        long parentDeadline = readDeadlineMs(context);

        // 传递深度信息到子链上下文（snapshot 为只读视图，须拷贝后再写入）
        Map<String, Object> snapshot = new HashMap<>(context.snapshot());
        snapshot.put("_sub_chain_depth", currentDepth + 1);

        ChainExecuteResultDTO result = chainExecutionEngine.executeWithDeadline(
                subChainCode, snapshot, parentDeadline);
        if (result.getStatus() == null || result.getStatus() != ChainConstants.CHAIN_SUCCESS) {
            String msg = result.getErrorMessage() != null
                    ? result.getErrorMessage()
                    : "子链执行失败 status=" + result.getStatus();
            throw new RuntimeException(msg);
        }
        log.debug("子链执行完成 nodeId={} subChainCode={}", nodeDef.getId(), subChainCode);
        return result.getResultData();
    }

    private static int getSubChainDepth(ChainContext context) {
        Object depth = context.get("_sub_chain_depth");
        if (depth instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static long readDeadlineMs(ChainContext context) {
        Object val = context.getMetadata(ChainConstants.META_DEADLINE_MS);
        if (val instanceof Number number) {
            return number.longValue();
        }
        return ChainInstance.NO_PARENT_DEADLINE;
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
        int maxCount = ChainConstants.MAX_ITERATOR_COUNT;
        if (items.size() > maxCount) {
            log.warn("迭代器数据源超过最大限制 nodeId={} size={} max={}，将截断处理",
                    nodeDef.getId(), items.size(), maxCount);
            items = new ArrayList<>(items).subList(0, maxCount);
        }

        String itemName = nodeDef.getIteratorItemName();
        List<NodeDefinition> subNodes = nodeDef.getIteratorSubNodes();

        if (subNodes == null || subNodes.isEmpty()) {
            log.warn("迭代器子节点为空，直接返回数据源 nodeId={}", nodeDef.getId());
            return new ArrayList<>(items);
        }

        List<Object> results = new ArrayList<>();
        int index = 0;
        for (Object item : items) {
            if (index >= maxCount) {
                log.warn("迭代器达到最大迭代次数 nodeId={} max={}", nodeDef.getId(), maxCount);
                break;
            }
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
        publishNodeEvent(ChainEvent.EventType.NODE_RETRYING, nodeDef, context, null, null, null, null, null);

        boolean retried = retryExecutor.executeWithRetry(
                nodeDef, context,
                retryCtx -> lifecycleExecutor.execute(nodeDef, retryCtx)
        );
        long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        if (retried) {
            stateMachine.transit(ChainConstants.NODE_SUCCESS);
            // retry 成功后不会回到 try 块的成功路径，此处补发完成事件
            publishNodeEvent(ChainEvent.EventType.NODE_COMPLETED, nodeDef, context, costMs, 1, null, null, null);

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
        if (!hasFallbackConfigured(nodeDef)) {
            stateMachine.transit(ChainConstants.NODE_FAILED);
            publishNodeEvent(ChainEvent.EventType.NODE_FAILED, nodeDef, context, costMs, 0,
                    "retry exhausted", null, null);
            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_FAILED)
                    .costMs(costMs)
                    .errorMessage("retry exhausted")
                    .build();
        }

        NodeResultDTO fallbackResult = handleFallback(nodeDef, context, stateMachine, startTime,
                new RuntimeException("retry exhausted"));

        if (fallbackResult.getStatus() == null || fallbackResult.getStatus() != ChainConstants.NODE_SUCCESS) {
            recordCircuitBreakerFailure(nodeDef, nodeDef.getId());
        }

        return fallbackResult;
    }

    private NodeResultDTO handleFallback(NodeDefinition nodeDef, ChainContext context,
                                          NodeStateMachine stateMachine, long startTime, Throwable cause) {
        publishNodeEvent(ChainEvent.EventType.NODE_FALLBACK_START, nodeDef, context, null, null, null, null, null);
        long costMs = 0;

        try {
            Object result;
            if (hasFallbackComponent(nodeDef)) {
                result = lifecycleExecutor.executeFallback(nodeDef, context, cause);
            } else {
                result = fallbackStrategy.fallback(nodeDef, context, cause);
            }
            costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            stateMachine.transit(ChainConstants.NODE_SUCCESS);
            publishNodeEvent(ChainEvent.EventType.NODE_FALLBACK_SUCCESS, nodeDef, context, costMs, 1, null, null,
                    toJsonString(result));
            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_SUCCESS)
                    .costMs(costMs)
                    .returnValue(result)
                    .outputData(context.snapshot())
                    .build();
        } catch (Exception fallbackError) {
            log.error("降级执行失败 nodeId={}", nodeDef.getId(), fallbackError);
            costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            stateMachine.transit(ChainConstants.NODE_FAILED);
            publishNodeEvent(ChainEvent.EventType.NODE_FALLBACK_FAILED, nodeDef, context, costMs, 0, fallbackError.getMessage(), null, null);
            return NodeResultDTO.builder()
                    .nodeId(nodeDef.getId())
                    .status(ChainConstants.NODE_FAILED)
                    .costMs(costMs)
                    .errorMessage(fallbackError.getMessage())
                    .build();
        }
    }

    private static boolean hasFallbackComponent(NodeDefinition nodeDef) {
        return nodeDef.getFallbackComponent() != null && !nodeDef.getFallbackComponent().isEmpty();
    }

    private static boolean hasFallbackConfigured(NodeDefinition nodeDef) {
        if (hasFallbackComponent(nodeDef)) {
            return true;
        }
        return nodeDef.getFallbackMode() != null && !nodeDef.getFallbackMode().isEmpty();
    }

    private void recordCircuitBreakerFailure(NodeDefinition nodeDef, String nodeId) {
        if (nodeDef.isCircuitBreakerEnabled()) {
            SimpleCircuitBreaker cb = circuitBreakers.get(nodeId);
            if (cb != null) cb.onFailure();
        }
    }

    private void publishNodeEvent(ChainEvent.EventType eventType, NodeDefinition nodeDef,
                                   ChainContext context, Long costMs, Integer status, String errorMessage,
                                   String params, String result) {
        if (eventPublisher == EventPublisher.NOOP) {
            return;
        }
        // 敏感数据脱敏
        String maskedParams = params;
        if (params != null && SensitiveDataMasker.containsSensitiveData(context.snapshot())) {
            Map<String, Object> masked = SensitiveDataMasker.mask(context.snapshot());
            try {
                maskedParams = JSON_MAPPER.writeValueAsString(masked);
            } catch (JsonProcessingException e) {
                maskedParams = params;
            }
        }
        eventPublisher.publish(ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .executionId(context.getInstanceId())
                .chainId(context.getChainCode())
                .chainName(resolveChainDisplayName(context))
                .nodeId(nodeDef.getComponent() != null ? nodeDef.getComponent() : nodeDef.getId())
                .nodeName(nodeDef.getLabel())
                .executorId(executorId)
                .appCode(appCode)
                .appName(appName)
                .tenantId(tenantId)
                .params(maskedParams)
                .result(result)
                .costMs(costMs)
                .status(status)
                .errorMessage(errorMessage)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private static String resolveChainDisplayName(ChainContext context) {
        if (context == null) {
            return null;
        }
        Object name = context.getMetadata(ChainConstants.META_CHAIN_NAME);
        if (name instanceof String s && !s.isEmpty()) {
            return s;
        }
        return context.getChainCode();
    }

    private static String toJsonString(Object obj) {
        if (obj == null) return null;
        try {
            return JSON_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("序列化事件数据失败", e);
            return null;
        }
    }

    private boolean evaluateCondition(String condition, ChainContext context) {
        return AviatorExpressionEvaluator.evaluateBoolean(condition, context.snapshot());
    }

    // ==================== 新增节点类型执行方法 ====================

    /**
     * SELECTOR 节点执行：多条件分支选择，返回路由标识供引擎选择匹配的出边
     */
    private Object executeSelector(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("SELECTOR 节点执行 nodeId={}", nodeDef.getId());
        
        executePreProcessors(nodeDef, context);
        
        String componentId = nodeDef.getComponent();
        if (componentId == null || componentId.isEmpty()) {
            log.warn("SELECTOR 节点未绑定元件，跳过执行 nodeId={}", nodeDef.getId());
            executePostProcessors(nodeDef, context);
            return null;
        }
        
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        
        // 将路由决策写入上下文，供引擎选择匹配的出边
        if (result != null) {
            String branchLabel = resolveBranchLabel(nodeDef, result.toString());
            if (branchLabel != null) {
                context.put("_branch", branchLabel);
                log.debug("SELECTOR 节点路由决策 nodeId={} result={} branch={}",
                        nodeDef.getId(), result, branchLabel);
            }
        }
        
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeFork(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("FORK 节点执行 nodeId={}", nodeDef.getId());
        context.put("_fork_node_id", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result != null ? result : "FORKED";
    }

    private Object executeJoin(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("JOIN 节点执行 nodeId={}", nodeDef.getId());
        context.put("_join_node_id", nodeDef.getId());
        Object forkId = context.get("_fork_node_id");
        if (forkId != null) {
            context.put("_join_fork_id", forkId);
        }
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result != null ? result : "JOINED";
    }

    private Object executeTryCatch(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("TRY_CATCH 节点执行 nodeId={}", nodeDef.getId());
        try {
            executePreProcessors(nodeDef, context);
            Object result = lifecycleExecutor.execute(nodeDef, context);
            publishResult(context, result, nodeDef);
            executePostProcessors(nodeDef, context);
            context.put("_branch", "Try");
            context.remove("_try_catch_error");
            context.remove("_try_catch_error_type");
            return result;
        } catch (Exception e) {
            log.warn("TRY_CATCH 节点捕获异常 nodeId={} error={}", nodeDef.getId(), e.getMessage());
            context.put("_try_catch_error", e.getMessage());
            context.put("_try_catch_error_type", e.getClass().getSimpleName());
            context.put("_branch", "Catch");
            return null;
        }
    }

    private Object executeWhile(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("WHILE 节点执行 nodeId={}", nodeDef.getId());
        String condition = nodeDef.getCondition();
        if (condition == null || condition.isEmpty()) {
            throw new IllegalArgumentException("WHILE 节点缺少循环条件 nodeId=" + nodeDef.getId());
        }

        int maxIterations = resolveMaxWhileIterations(nodeDef);
        int iteration = 0;
        List<Object> results = new ArrayList<>();

        while (evaluateCondition(condition, context) && iteration < maxIterations) {
            context.put("_while_index", iteration);
            executePreProcessors(nodeDef, context);
            Object result = lifecycleExecutor.execute(nodeDef, context);
            publishResult(context, result, nodeDef);
            executePostProcessors(nodeDef, context);
            if (result != null) {
                results.add(result);
            }
            iteration++;
        }

        log.debug("WHILE 节点完成 nodeId={} iterations={}", nodeDef.getId(), iteration);
        return results;
    }

    private Object executeApproval(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("APPROVAL 节点执行 nodeId={}", nodeDef.getId());
        String approvalStatus = (String) context.get("_approval_status");
        if ("APPROVED".equals(approvalStatus)) {
            log.debug("审批已通过，继续执行 nodeId={}", nodeDef.getId());
            executePreProcessors(nodeDef, context);
            Object result = lifecycleExecutor.execute(nodeDef, context);
            publishResult(context, result, nodeDef);
            executePostProcessors(nodeDef, context);
            return result;
        }
        if ("REJECTED".equals(approvalStatus)) {
            throw new RuntimeException("审批被拒绝 nodeId=" + nodeDef.getId());
        }
        // 挂起等待审批
        log.debug("审批节点等待人工审批 nodeId={}", nodeDef.getId());
        context.put("_approval_pending", nodeDef.getId());
        return "PENDING_APPROVAL";
    }

    private Object executeNotification(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("NOTIFICATION 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeTransformer(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("TRANSFORMER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeFilter(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("FILTER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeAggregator(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("AGGREGATOR 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeSplitter(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("SPLITTER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeHttpClient(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("HTTP_CLIENT 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result;
        String component = nodeDef.getComponent();
        if (component == null || component.isBlank()) {
            if (context.get("_http_url") != null) {
                String method = context.get("_http_method", String.class);
                result = NativeHttpClient.execute(context, method);
            } else {
                component = resolveBuiltinHttpComponent(nodeDef, context);
                result = lifecycleExecutor.execute(withComponent(nodeDef, component), context);
            }
        } else {
            result = lifecycleExecutor.execute(nodeDef, context);
        }
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private static NodeDefinition withComponent(NodeDefinition nodeDef, String component) {
        return NodeDefinition.builder()
                .id(nodeDef.getId())
                .type(nodeDef.getType())
                .label(nodeDef.getLabel())
                .component(component)
                .config(nodeDef.getConfig())
                .timeout(nodeDef.getTimeout())
                .build();
    }

    private static String resolveBuiltinHttpComponent(NodeDefinition nodeDef, ChainContext context) {
        String method = context.get("_http_method", String.class);
        if (method == null && nodeDef.getConfig() != null && nodeDef.getConfig().get("httpMethod") != null) {
            method = String.valueOf(nodeDef.getConfig().get("httpMethod"));
        }
        if (method == null) {
            method = "GET";
        }
        return switch (method.toUpperCase()) {
            case "POST" -> "httpPost";
            case "PUT" -> "httpPut";
            case "DELETE" -> "httpDelete";
            default -> "httpGet";
        };
    }

    private Object executeMqProducer(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("MQ_PRODUCER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeMqConsumer(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("MQ_CONSUMER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeCacheReader(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("CACHE_READER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeCacheWriter(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("CACHE_WRITER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeLogger(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        log.debug("LOGGER 节点执行 nodeId={}", nodeDef.getId());
        executePreProcessors(nodeDef, context);
        Object result = lifecycleExecutor.execute(nodeDef, context);
        publishResult(context, result, nodeDef);
        executePostProcessors(nodeDef, context);
        return result;
    }

    private Object executeDelay(NodeDefinition nodeDef, ChainContext context, NodeStateMachine stateMachine) {
        long delayMs = resolveDelayMs(nodeDef);
        log.debug("DELAY 节点执行 nodeId={} delayMs={}", nodeDef.getId(), delayMs);
        long deadline = System.currentTimeMillis() + delayMs;
        while (System.currentTimeMillis() < deadline) {
            if (context.isExecutionStopped()) {
                throw new RuntimeException("延迟节点被终止 nodeId=" + nodeDef.getId());
            }
            long remaining = deadline - System.currentTimeMillis();
            try {
                Thread.sleep(Math.min(remaining, 100L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("延迟节点被中断 nodeId=" + nodeDef.getId(), e);
            }
        }
        return "DELAYED_" + delayMs + "ms";
    }

    /** 将设计器 config 注入上下文，供元件读取（对标 n8n/Camunda 字段映射） */
    private void applyNodeConfigToContext(NodeDefinition nodeDef, ChainContext context) {
        NodeConfigBridge.apply(nodeDef, context);
    }

    private long resolveDelayMs(NodeDefinition nodeDef) {
        Map<String, Object> cfg = nodeDef.getConfig();
        if (cfg != null && cfg.containsKey("delayMs")) {
            Object raw = cfg.get("delayMs");
            if (raw instanceof Number number) {
                return clampDelay(number.longValue());
            }
            try {
                return clampDelay(Long.parseLong(String.valueOf(raw)));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        long configured = nodeDef.getTimeout();
        if (configured > 2_000L) {
            return clampDelay(configured - 2_000L);
        }
        return clampDelay(configured);
    }

    private static long clampDelay(long delayMs) {
        if (delayMs <= 0) {
            return 1L;
        }
        return Math.min(delayMs, ChainConstants.MAX_DELAY_MS);
    }

    private int resolveMaxWhileIterations(NodeDefinition nodeDef) {
        Map<String, Object> cfg = nodeDef.getConfig();
        if (cfg != null && cfg.containsKey("maxIterations")) {
            Object raw = cfg.get("maxIterations");
            if (raw instanceof Number number) {
                return Math.min(Math.max(1, number.intValue()), ChainConstants.MAX_WHILE_ITERATIONS);
            }
            try {
                return Math.min(Math.max(1, Integer.parseInt(String.valueOf(raw))), ChainConstants.MAX_WHILE_ITERATIONS);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return ChainConstants.MAX_WHILE_ITERATIONS;
    }
}
