package com.zestflow.executor.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.ChainLoader;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.ChainStateMachine;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 默认链执行引擎实现
 * <p>
 * 执行流程：
 * <ol>
 *   <li>解析 ChainDefinition（StampedLock 无锁读）</li>
 *   <li>创建 ChainInstance + ChainContext</li>
 *   <li>调用拦截器链前置</li>
 *   <li>Kahn 拓扑排序 → 按层执行（同层并行，ForkJoinPool）</li>
 *   <li>每节点：NodeRunner 执行完整生命周期管线</li>
 *   <li>结果聚合 → 更新上下文</li>
 *   <li>调用拦截器链后置</li>
 *   <li>发布完成事件</li>
 *   <li>返回 ChainExecuteResultDTO</li>
 * </ol>
 */
@Slf4j
public class DefaultChainExecutionEngine implements ChainExecutionEngine {

    private final ChainManager chainManager;
    private ChainLoader chainLoader;
    private final DagSorter dagSorter;
    private final NodeRunner nodeRunner;
    private final ChainInstanceManager instanceManager;
    private final EventPublisher eventPublisher;
    private final InterceptorChain interceptorChain;
    private final ExecutorProperties properties;
    private final String appCode;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Setter 注入 ChainLoader（setter 打破循环依赖：engine → loader → nodeRunner → engine） */
    public void setChainLoader(ChainLoader chainLoader) {
        this.chainLoader = chainLoader;
    }

    /** 并行执行线程池 */
    private final ForkJoinPool forkJoinPool = new ForkJoinPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));

    /** Future.get 安全上限，避免 Long.MAX_VALUE 转 nanos 溢出 */
    private static final long MAX_FUTURE_WAIT_MS = Integer.MAX_VALUE - 1L;

    public DefaultChainExecutionEngine(ChainManager chainManager,
                                       DagSorter dagSorter, NodeRunner nodeRunner,
                                       ChainInstanceManager instanceManager,
                                       EventPublisher eventPublisher, InterceptorChain interceptorChain,
                                       ExecutorProperties properties) {
        this.chainManager = chainManager;
        this.dagSorter = dagSorter;
        this.nodeRunner = nodeRunner;
        this.instanceManager = instanceManager;
        this.eventPublisher = eventPublisher != null ? eventPublisher : EventPublisher.noop();
        this.interceptorChain = interceptorChain;
        this.properties = properties;
        this.appCode = properties.getAppCode();
    }

    /** 关闭 ForkJoinPool + 清理过期实例，释放线程资源 */
    public void destroy() {
        long graceMs = properties.getShutdownGracePeriodMs();
        if (graceMs > 0) {
            int running = instanceManager.countRunning();
            if (running > 0) {
                log.info("等待在途链执行完成 running={} graceMs={}", running, graceMs);
                try {
                    if (!instanceManager.awaitIdle(graceMs)) {
                        log.warn("关闭宽限期结束，仍有 {} 条链在执行", instanceManager.countRunning());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        instanceManager.cleanupCompleted();
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public ChainExecuteResultDTO execute(String chainCode, Object... args) {
        return doExecute(chainCode, null, ChainInstance.NO_PARENT_DEADLINE, args);
    }

    @Override
    public ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args) {
        return doExecute(chainCode, params, ChainInstance.NO_PARENT_DEADLINE, args);
    }

    @Override
    public ChainExecuteResultDTO executeWithDeadline(String chainCode, Map<String, Object> params,
                                                      long parentDeadlineMs) {
        return doExecute(chainCode, params, parentDeadlineMs);
    }

    private ChainExecuteResultDTO doExecute(String chainCode, Map<String, Object> params,
                                               long parentDeadlineMs, Object... typedArgs) {
        long startTime = System.currentTimeMillis();
        log.info("链执行开始 chainCode={}", chainCode);

        // 1. 获取链定义（自动从 DB 加载未注册的链）
        ChainDefinition definition = chainManager.get(chainCode);
        if (definition == null) {
            log.info("链定义未在内存中找到，尝试从 DB 加载 chainCode={}", chainCode);
            var loadResult = chainLoader.reloadChainLocal(chainCode, null, null);
            if (loadResult.isSuccess()) {
                definition = chainManager.get(chainCode);
            }
        }
        if (definition == null) {
            return ChainExecuteResultDTO.builder()
                    .chainCode(chainCode)
                    .status(ChainConstants.CHAIN_FAILED)
                    .errorMessage("链定义不存在: " + chainCode)
                    .build();
        }

        // 2. 创建实例（子链继承父链 deadline）
        ChainInstance instance = new ChainInstance(definition, params, parentDeadlineMs);
        String chainDisplayName = chainLoader.resolveChainDisplayName(chainCode);
        if (chainDisplayName == null) {
            chainDisplayName = chainCode;
        }
        instance.getContext().setMetadata(ChainConstants.META_CHAIN_NAME, chainDisplayName);
        instanceManager.register(instance);

        // 3. 注册类型化参数到上下文
        if (typedArgs != null) {
            ChainContext ctx = instance.getContext();
            for (Object arg : typedArgs) {
                if (arg != null) {
                    ctx.register(arg);
                }
            }
        }

        try {
            ChainContext context = instance.getContext();
            context.setMetadata(ChainConstants.META_STOP_CHECK, (BooleanSupplier) instance::isStopped);
            ChainStateMachine stateMachine = instance.getStateMachine();
            List<NodeResultDTO> allNodeResults = new ArrayList<>();
            List<String> succeededNodeIds = new ArrayList<>();

            stateMachine.transit(ChainConstants.CHAIN_LOADING);
            stateMachine.transit(ChainConstants.CHAIN_READY);
            stateMachine.transit(ChainConstants.CHAIN_RUNNING);
            publishChainEvent(ChainEvent.EventType.CHAIN_STARTED, chainCode, instance);

            // 3. 拦截器前置
            interceptorChain.beforeChain(chainCode, context);

            // 4. 获取拓扑分层
            List<List<String>> layers = dagSorter.sort(definition);

            // 5. 逐层执行 — 动态追踪条件可达节点，实现条件边路由
            //    初始时第一层（入度为 0 的节点）全部可达
            Set<String> reachableNodes = new HashSet<>();
            if (!layers.isEmpty()) {
                reachableNodes.addAll(layers.get(0));
            }

            for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                List<String> layerNodeIds = layers.get(layerIndex);

                // 按运行时条件过滤仅执行可达节点
                List<String> executableNodeIds = layerNodeIds.stream()
                        .filter(reachableNodes::contains)
                        .collect(Collectors.toList());

                log.debug("链执行第 {} 层 chainCode={} nodes={}", layerIndex + 1, chainCode, executableNodeIds);

                if (executableNodeIds.isEmpty()) {
                    reachableNodes.clear();
                    continue;
                }

                if (instance.isStopped()) {
                    log.warn("链执行被终止 chainCode={} instanceId={}", chainCode, instance.getInstanceId());
                    stateMachine.transit(ChainConstants.CHAIN_STOPPED);
                    break;
                }

                if (instance.isTimedOut()) {
                    instance.markStopped();
                    log.warn("链执行超时 chainCode={} deadlineMs={}", chainCode, instance.getDeadlineMs());
                    stateMachine.transit(ChainConstants.CHAIN_TIMEOUT);
                    publishChainEvent(ChainEvent.EventType.CHAIN_TIMEOUT, chainCode, instance);
                    break;
                }

                List<NodeResultDTO> layerResults = executeLayer(executableNodeIds, definition, context, instance);
                allNodeResults.addAll(layerResults);
                layerResults.stream()
                        .filter(r -> !isNodeFailure(r))
                        .map(NodeResultDTO::getNodeId)
                        .filter(Objects::nonNull)
                        .forEach(succeededNodeIds::add);

                // 计算当前层可达的下一层节点（按条件边动态路由）
                reachableNodes.clear();
                for (String nodeId : executableNodeIds) {
                    List<String> successors = resolveNodeSuccessors(nodeId, definition, context);
                    reachableNodes.addAll(successors);
                }

                if (instance.isStopped()) {
                    log.warn("链执行被终止 chainCode={} instanceId={}", chainCode, instance.getInstanceId());
                    stateMachine.transit(ChainConstants.CHAIN_STOPPED);
                    break;
                }

                boolean hasFailed = layerResults.stream().anyMatch(this::isNodeFailure);
                if (hasFailed) {
                    String errorStrategy = definition.getErrorStrategy();
                    String nodeError = layerResults.stream()
                            .filter(this::isNodeFailure)
                            .map(NodeResultDTO::getErrorMessage)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    if (nodeError != null) {
                        instance.getContext().put("_errorMessage", nodeError);
                    }

                    if (ChainConstants.ERROR_STRATEGY_STOP.equals(errorStrategy)) {
                        log.warn("节点执行失败，终止链执行 chainCode={}", chainCode);
                        stateMachine.transit(ChainConstants.CHAIN_FAILED);
                        break;
                    }
                    if (ChainConstants.ERROR_STRATEGY_COMPENSATE.equals(errorStrategy)) {
                        log.warn("节点执行失败，触发补偿 chainCode={} succeeded={}", chainCode, succeededNodeIds.size());
                        List<NodeResultDTO> compResults = runCompensation(
                                definition, context, succeededNodeIds, instance);
                        allNodeResults.addAll(compResults);
                        boolean compFailed = compResults.stream().anyMatch(this::isNodeFailure);
                        stateMachine.transit(compFailed ? ChainConstants.CHAIN_FAILED : ChainConstants.CHAIN_COMPENSATED);
                        break;
                    }
                    // CONTINUE：记录失败但继续后续层
                }
            }

            applyContinuePartialSuccess(definition, context, allNodeResults, stateMachine);

            // 6. 拦截器后置
            interceptorChain.afterChain(chainCode, context, allNodeResults);

            // 7. 发布终态事件（超时已在层间发布，避免重复发 FAILED）
            publishFinalChainEvent(chainCode, instance, stateMachine);

            long costMs = System.currentTimeMillis() - startTime;
            log.info("链执行完成 chainCode={} status={} cost={}ms nodes={}",
                    chainCode, stateMachine.current(), costMs, allNodeResults.size());

            String errorMsg = (String) context.get("_errorMessage");
            return ChainExecuteResultDTO.builder()
                    .instanceId(instance.getInstanceId())
                    .chainCode(chainCode)
                    .status(stateMachine.current())
                    .costMs(costMs)
                    .errorMessage(errorMsg)
                    .resultData(context.snapshot())
                    .resultTypedData(context.typedSnapshot())
                    .nodeResults(allNodeResults)
                    .build();

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("链执行异常 chainCode={} cost={}ms", chainCode, costMs, e);

            instance.getStateMachine().transit(ChainConstants.CHAIN_FAILED);
            publishChainEvent(ChainEvent.EventType.CHAIN_FAILED, chainCode, instance);

            return ChainExecuteResultDTO.builder()
                    .instanceId(instance.getInstanceId())
                    .chainCode(chainCode)
                    .status(ChainConstants.CHAIN_FAILED)
                    .costMs(costMs)
                    .errorMessage(e.getMessage())
                    .build();
        } finally {
            instanceManager.unregister(instance.getInstanceId());
        }
    }

    @Override
    public CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Object... args) {
        return CompletableFuture.supplyAsync(() -> execute(chainCode, args), forkJoinPool);
    }

    @Override
    public boolean stop(String instanceId) {
        return instanceManager.stop(instanceId);
    }

    @Override
    public int stopByChain(String chainCode) {
        return instanceManager.stopByChain(chainCode);
    }

    @Override
    public List<ChainInstance> listRunning(String chainCode) {
        return instanceManager.listByChainCode(chainCode);
    }

    private void publishFinalChainEvent(String chainCode, ChainInstance instance, ChainStateMachine stateMachine) {
        if (eventPublisher == EventPublisher.NOOP) {
            return;
        }
        if (!stateMachine.isTerminated()) {
            stateMachine.transit(ChainConstants.CHAIN_SUCCESS);
        }
        int status = stateMachine.current();
        ChainEvent.EventType eventType = switch (status) {
            case ChainConstants.CHAIN_SUCCESS -> ChainEvent.EventType.CHAIN_COMPLETED;
            case ChainConstants.CHAIN_COMPENSATED -> ChainEvent.EventType.CHAIN_COMPENSATED;
            case ChainConstants.CHAIN_TIMEOUT -> null;
            case ChainConstants.CHAIN_STOPPED, ChainConstants.CHAIN_FAILED -> ChainEvent.EventType.CHAIN_FAILED;
            default -> ChainEvent.EventType.CHAIN_FAILED;
        };
        if (eventType != null) {
            publishChainEvent(eventType, chainCode, instance);
        }
    }

    private boolean isNodeFailure(NodeResultDTO result) {
        if (result == null) {
            return true;
        }
        int status = result.getStatus();
        return status == ChainConstants.NODE_FAILED || status == ChainConstants.NODE_TIMEOUT;
    }

    /**
     * 节点级超时执行（对标 xxl-job 任务超时 + LiteFlow 组件 timeout）。
     * effectiveTimeout = min(链剩余预算, nodeDef.timeout)。
     */
    private NodeResultDTO invokeNodeWithTimeout(NodeDefinition nodeDef, ChainContext context,
                                                 ChainInstance instance) {
        if (instance.isStopped()) {
            return nodeFailure(nodeDef.getId(), ChainConstants.NODE_FAILED, "链执行已终止");
        }
        if (instance.isTimedOut()) {
            return nodeFailure(nodeDef.getId(), ChainConstants.NODE_TIMEOUT, "链执行超时");
        }

        long timeoutMs = resolveNodeTimeoutMs(nodeDef, instance);
        if (timeoutMs == 0) {
            return nodeFailure(nodeDef.getId(), ChainConstants.NODE_TIMEOUT, "链执行超时");
        }
        if (timeoutMs >= ChainInstance.NO_PARENT_DEADLINE) {
            return nodeRunner.execute(nodeDef, context);
        }

        CompletableFuture<NodeResultDTO> future = CompletableFuture.supplyAsync(
                () -> nodeRunner.execute(nodeDef, context), forkJoinPool);
        try {
            return future.get(safeWaitMs(timeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("节点执行超时 nodeId={} timeout={}ms chainCode={}",
                    nodeDef.getId(), timeoutMs, instance.getChainCode());
            return nodeFailure(nodeDef.getId(), ChainConstants.NODE_TIMEOUT, "节点执行超时");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("节点执行异常 nodeId={}", nodeDef.getId(), cause);
            return nodeFailure(nodeDef.getId(), ChainConstants.NODE_FAILED, cause.getMessage());
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return nodeFailure(nodeDef.getId(), ChainConstants.NODE_FAILED, "节点执行被中断");
        }
    }

    private static long resolveNodeTimeoutMs(NodeDefinition nodeDef, ChainInstance instance) {
        long remaining = instance.getRemainingMs();
        long nodeTimeout = nodeDef.getTimeout();
        boolean nodeUnlimited = nodeTimeout <= 0 || nodeTimeout == ChainConstants.NODE_TIMEOUT_UNLIMITED;
        if (nodeUnlimited && remaining >= ChainInstance.NO_PARENT_DEADLINE) {
            return ChainInstance.NO_PARENT_DEADLINE;
        }
        if (nodeUnlimited) {
            return remaining;
        }
        if (remaining >= ChainInstance.NO_PARENT_DEADLINE) {
            return nodeTimeout;
        }
        return Math.min(remaining, nodeTimeout);
    }

    private static long safeWaitMs(long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }
        if (timeoutMs >= ChainInstance.NO_PARENT_DEADLINE) {
            return MAX_FUTURE_WAIT_MS;
        }
        return Math.min(timeoutMs, MAX_FUTURE_WAIT_MS);
    }

    private static NodeResultDTO nodeFailure(String nodeId, int status, String message) {
        return NodeResultDTO.builder()
                .nodeId(nodeId)
                .status(status)
                .errorMessage(message)
                .build();
    }

    private static void cancelPendingFutures(Iterable<? extends CompletableFuture<?>> futures) {
        for (CompletableFuture<?> future : futures) {
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }
    }

    /** CONTINUE 策略：部分节点失败时标记上下文，终态仍为 CHAIN_SUCCESS */
    private void applyContinuePartialSuccess(ChainDefinition definition, ChainContext context,
                                                List<NodeResultDTO> allNodeResults,
                                                ChainStateMachine stateMachine) {
        if (stateMachine.isTerminated()) {
            return;
        }
        if (!ChainConstants.ERROR_STRATEGY_CONTINUE.equals(definition.getErrorStrategy())) {
            return;
        }
        List<String> failedIds = allNodeResults.stream()
                .filter(this::isNodeFailure)
                .map(NodeResultDTO::getNodeId)
                .filter(Objects::nonNull)
                .toList();
        if (failedIds.isEmpty()) {
            return;
        }
        context.put(ChainConstants.CTX_PARTIAL_FAILURE, true);
        context.put(ChainConstants.CTX_FAILED_NODE_IDS, failedIds);
        log.warn("链部分成功 chainCode={} failedNodes={}", definition.getCode(), failedIds);
    }

    /**
     * COMPENSATE 策略：按成功节点的逆序执行补偿（对标 LiteFlow rollback 链）。
     */
    private List<NodeResultDTO> runCompensation(ChainDefinition definition, ChainContext context,
                                                 List<String> succeededNodeIds, ChainInstance instance) {
        if (succeededNodeIds.isEmpty()) {
            return List.of();
        }
        List<String> reverseOrder = new ArrayList<>(succeededNodeIds);
        Collections.reverse(reverseOrder);

        List<NodeResultDTO> results = new ArrayList<>();
        for (String nodeId : reverseOrder) {
            if (instance.isStopped() || instance.isTimedOut()) {
                results.add(nodeFailure(nodeId, ChainConstants.NODE_FAILED, "链执行已终止"));
                break;
            }
            NodeDefinition nodeDef = definition.getNode(nodeId);
            if (nodeDef == null) {
                continue;
            }
            results.add(nodeRunner.compensate(nodeDef, context));
            NodeResultDTO last = results.get(results.size() - 1);
            if (isNodeFailure(last)) {
                log.warn("补偿失败，中止后续补偿 nodeId={}", nodeId);
                break;
            }
        }
        return results;
    }

    /**
     * 发布链级事件
     */
    private void publishChainEvent(ChainEvent.EventType eventType, String chainCode, ChainInstance instance) {
        if (eventPublisher == EventPublisher.NOOP) {
            return;
        }
        ChainContext context = instance.getContext();
        String chainDisplayName = resolveChainDisplayName(context);
        eventPublisher.publish(ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .executionId(instance.getInstanceId())
                .chainId(chainCode)
                .chainName(chainDisplayName)
                .executorId(properties.getAppCode() + "@" + properties.getHost() + ":" + properties.getPort())
                .appCode(appCode)
                .appName(properties.getAppName())
                .tenantId(properties.getTenantId())
                .params(toJsonString(context != null ? context.snapshot() : null))
                .result(toJsonString(context != null ? context.snapshot() : null))
                .timestamp(System.currentTimeMillis())
                .costMs(instance.elapsed())
                .status(eventType == ChainEvent.EventType.CHAIN_COMPLETED ? 1 : 0)
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
        if (obj == null) {
            return null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("序列化事件数据失败", e);
            return null;
        }
    }

    private List<NodeResultDTO> executeLayer(List<String> nodeIds,
                                              ChainDefinition definition,
                                              ChainContext context,
                                              ChainInstance instance) {
        if (nodeIds.isEmpty()) {
            return List.of();
        }
        if (nodeIds.size() == 1) {
            NodeDefinition nodeDef = definition.getNode(nodeIds.get(0));
            if (nodeDef == null) {
                return List.of();
            }
            return List.of(invokeNodeWithTimeout(nodeDef, context, instance));
        }

        int parallelThreshold = definition.getParallelThreshold();
        boolean anyAsync = nodeIds.stream()
                .map(definition::getNode)
                .filter(Objects::nonNull)
                .anyMatch(NodeDefinition::isAsync);
        boolean useParallelFork = nodeIds.size() >= parallelThreshold || anyAsync;

        if (!useParallelFork) {
            return executeLayerSequential(nodeIds, definition, context, instance);
        }

        return executeLayerParallel(nodeIds, definition, context, instance);
    }

    private List<NodeResultDTO> executeLayerSequential(List<String> nodeIds,
                                                        ChainDefinition definition,
                                                        ChainContext context,
                                                        ChainInstance instance) {
        List<NodeResultDTO> results = new ArrayList<>();
        for (String nodeId : nodeIds) {
            if (instance.isStopped() || instance.isTimedOut()) {
                break;
            }
            NodeDefinition nodeDef = definition.getNode(nodeId);
            if (nodeDef == null) {
                continue;
            }
            results.add(invokeNodeWithTimeout(nodeDef, context, instance));
        }
        return results;
    }

    private List<NodeResultDTO> executeLayerParallel(List<String> nodeIds,
                                                      ChainDefinition definition,
                                                      ChainContext context,
                                                      ChainInstance instance) {
        List<CompletableFuture<ParallelNodeOutcome>> futures = new ArrayList<>();

        for (String nodeId : nodeIds) {
            NodeDefinition nodeDef = definition.getNode(nodeId);
            if (nodeDef == null) {
                continue;
            }
            ChainContext forkedContext = context.fork();
            futures.add(CompletableFuture.supplyAsync(
                    () -> new ParallelNodeOutcome(
                            invokeNodeWithTimeout(nodeDef, forkedContext, instance), forkedContext),
                    forkJoinPool));
        }

        if (futures.isEmpty()) {
            return List.of();
        }

        if (instance.isStopped() || instance.isTimedOut()) {
            cancelPendingFutures(futures);
            return List.of();
        }

        long waitMs = safeWaitMs(instance.getRemainingMs());
        if (waitMs == 0 && instance.hasDeadline()) {
            cancelPendingFutures(futures);
            return List.of(nodeFailure(null, ChainConstants.NODE_TIMEOUT, "并行层执行超时"));
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        try {
            all.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("并行层等待超时 chainCode={} instanceId={}",
                    definition.getCode(), instance.getInstanceId());
            cancelPendingFutures(futures);
            return List.of(nodeFailure(null, ChainConstants.NODE_TIMEOUT, "并行层执行超时"));
        } catch (Exception e) {
            log.error("并行层执行异常 chainCode={}", definition.getCode(), e);
            cancelPendingFutures(futures);
            return List.of(nodeFailure(null, ChainConstants.NODE_FAILED, e.getMessage()));
        }

        List<NodeResultDTO> results = new ArrayList<>();
        for (CompletableFuture<ParallelNodeOutcome> future : futures) {
            ParallelNodeOutcome outcome = future.getNow(null);
            if (outcome == null) {
                results.add(nodeFailure(null, ChainConstants.NODE_FAILED, "并行节点未完成"));
                continue;
            }
            context.mergeFrom(outcome.forkedContext());
            results.add(outcome.result());
        }
        return results;
    }

    private record ParallelNodeOutcome(NodeResultDTO result, ChainContext forkedContext) {
    }

    /**
     * 解析节点的运行时可达后继
     * <p>
     * 决策优先级：
     * <ol>
     *   <li>出边存在 condition → DagSorter 条件评估</li>
     *   <li>节点为 CONDITION 类型且出边有 label → 用上下文中 {@code _branch} 匹配 label</li>
     *   <li>否则全部可达（无条件边）</li>
     * </ol>
     */
    private List<String> resolveNodeSuccessors(String nodeId, ChainDefinition definition, ChainContext context) {
        NodeDefinition nodeDef = definition.getNode(nodeId);
        // 非 CONDITION 节点走 DagSorter 标准逻辑
        if (nodeDef == null || !ChainConstants.NODE_TYPE_CONDITION.equals(nodeDef.getType())) {
            return dagSorter.resolveReachableSuccessors(nodeId, definition, context.snapshot());
        }

        // CONDITION 节点：检查出边是否有 condition
        List<ChainEdge> outgoingEdges = definition.getEdges().stream()
                .filter(e -> e.getSource().equals(nodeId))
                .toList();
        boolean hasConditionalEdges = outgoingEdges.stream()
                .anyMatch(e -> e.getCondition() != null && !e.getCondition().isEmpty());

        if (hasConditionalEdges) {
            // 边上有 condition，走 DagSorter 条件评估
            return dagSorter.resolveReachableSuccessors(nodeId, definition, context.snapshot());
        }

        // 边无 condition：用 _branch 约定键匹配 label
        Map<String, Object> snapshot = context.snapshot();
        Object branchValue = snapshot.get("_branch");
        if (branchValue != null) {
            String branchStr = branchValue.toString();
            for (ChainEdge edge : outgoingEdges) {
                if (edge.getLabel() != null && edge.getLabel().equals(branchStr)) {
                    log.debug("CONDITION 节点路由匹配 nodeId={} branch={} target={}",
                            nodeId, branchStr, edge.getTarget());
                    return List.of(edge.getTarget());
                }
            }
            log.warn("CONDITION 节点未匹配到分支 nodeId={} _branch={} labels={}",
                    nodeId, branchStr, outgoingEdges.stream().map(ChainEdge::getLabel).toList());
            // _branch 已设置但无匹配标签 → 停止该分支，不走无条件边（防全分支执行）
            return List.of();
        }

        // 无 _branch：CONDITION 纯路由器（无组件但有条件表达式）
        // 条件已通过 evaluateCondition 验证，所有无条件边均可达
        List<String> unconditionalTargets = outgoingEdges.stream()
                .filter(e -> e.getCondition() == null || e.getCondition().isEmpty())
                .map(ChainEdge::getTarget)
                .toList();
        if (!unconditionalTargets.isEmpty()) {
            return unconditionalTargets;
        }
        return List.of();
    }
}
