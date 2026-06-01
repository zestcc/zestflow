package com.zestflow.executor.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.common.spi.EventCollector;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
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
    private final EventCollector eventCollector;
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

    public DefaultChainExecutionEngine(ChainManager chainManager,
                                       DagSorter dagSorter, NodeRunner nodeRunner,
                                       ChainInstanceManager instanceManager,
                                       EventCollector eventCollector, InterceptorChain interceptorChain,
                                       ExecutorProperties properties) {
        this.chainManager = chainManager;
        this.dagSorter = dagSorter;
        this.nodeRunner = nodeRunner;
        this.instanceManager = instanceManager;
        this.eventCollector = eventCollector;
        this.interceptorChain = interceptorChain;
        this.properties = properties;
        this.appCode = properties.getAppCode();
    }

    /** 关闭 ForkJoinPool + 清理过期实例，释放线程资源 */
    public void destroy() {
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
        return doExecute(chainCode, null, args);
    }

    @Override
    public ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params, Object... args) {
        return doExecute(chainCode, params, args);
    }

    private ChainExecuteResultDTO doExecute(String chainCode, Map<String, Object> params, Object... typedArgs) {
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

        // 2. 创建实例
        ChainInstance instance = new ChainInstance(definition, params);
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
            ChainStateMachine stateMachine = instance.getStateMachine();
            List<NodeResultDTO> allNodeResults = new ArrayList<>();

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

                if (instance.elapsed() > definition.getTimeout()) {
                    log.warn("链执行超时 chainCode={} timeout={}ms", chainCode, definition.getTimeout());
                    stateMachine.transit(ChainConstants.CHAIN_TIMEOUT);
                    publishChainEvent(ChainEvent.EventType.CHAIN_TIMEOUT, chainCode, instance);
                    break;
                }

                List<NodeResultDTO> layerResults = executeLayer(executableNodeIds, definition, context, instance);
                allNodeResults.addAll(layerResults);

                // 计算当前层可达的下一层节点（按条件边动态路由）
                reachableNodes.clear();
                for (String nodeId : executableNodeIds) {
                    List<String> successors = resolveNodeSuccessors(nodeId, definition, context);
                    reachableNodes.addAll(successors);
                }

                boolean hasFailed = layerResults.stream()
                        .anyMatch(r -> r.getStatus() == ChainConstants.NODE_FAILED);
                if (hasFailed && ChainConstants.ERROR_STRATEGY_STOP.equals(definition.getErrorStrategy())) {
                    log.warn("节点执行失败，终止链执行 chainCode={}", chainCode);
                    // 从失败节点提取错误信息
                    String nodeError = layerResults.stream()
                            .filter(r -> r.getStatus() == ChainConstants.NODE_FAILED)
                            .map(NodeResultDTO::getErrorMessage)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    if (nodeError != null) {
                        instance.getContext().put("_errorMessage", nodeError);
                    }
                    stateMachine.transit(ChainConstants.CHAIN_FAILED);
                    break;
                }
            }

            // 6. 拦截器后置
            interceptorChain.afterChain(chainCode, context, allNodeResults);

            // 7. 发布完成事件
            if (!stateMachine.isTerminated()) {
                stateMachine.transit(ChainConstants.CHAIN_SUCCESS);
            }
            ChainEvent.EventType finalType = stateMachine.current() == ChainConstants.CHAIN_SUCCESS
                    ? ChainEvent.EventType.CHAIN_COMPLETED : ChainEvent.EventType.CHAIN_FAILED;
            publishChainEvent(finalType, chainCode, instance);

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

    /**
     * 发布链级事件
     */
    private void publishChainEvent(ChainEvent.EventType eventType, String chainCode, ChainInstance instance) {
        if (eventCollector == null) {
            return;
        }
        ChainContext context = instance.getContext();
        eventCollector.collect(ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .executionId(instance.getInstanceId())
                .chainId(chainCode)
                .chainName(chainCode)
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

    private static String toJsonString(Object obj) {
        if (obj == null) return null;
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
        if (nodeIds.size() == 1) {
            NodeDefinition nodeDef = definition.getNode(nodeIds.get(0));
            if (nodeDef == null) return List.of();
            return List.of(nodeRunner.execute(nodeDef, context));
        }

        List<CompletableFuture<NodeResultDTO>> futures = new ArrayList<>();

        for (String nodeId : nodeIds) {
            NodeDefinition nodeDef = definition.getNode(nodeId);
            if (nodeDef == null) continue;

            if (nodeDef.isAsync() || nodeIds.size() > 1) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> nodeRunner.execute(nodeDef, context), forkJoinPool));
            } else {
                futures.add(CompletableFuture.completedFuture(
                        nodeRunner.execute(nodeDef, context)));
            }
        }

        long chainTimeout = definition.getTimeout();
        return futures.stream()
                .map(f -> {
                    try {
                        long remainingTime = chainTimeout - instance.elapsed();
                        long timeout = Math.min(
                                nodeDefTimeout(chainTimeout, remainingTime),
                                Math.max(remainingTime, 1000));
                        return f.get(timeout, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.error("并行节点执行异常", e);
                        return NodeResultDTO.builder()
                                .status(ChainConstants.NODE_FAILED)
                                .errorMessage(e.getMessage())
                                .build();
                    }
                })
                .collect(Collectors.toList());
    }

    /** 取节点超时与剩余时间的最小值 */
    private static long nodeDefTimeout(long chainTimeout, long remainingTime) {
        return Math.min(chainTimeout, Math.max(remainingTime, 0));
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
