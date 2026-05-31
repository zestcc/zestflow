package com.zestflow.executor.engine;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.NodeResultDTO;
import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.event.EventPublisher;
import com.zestflow.executor.interceptor.InterceptorChain;
import com.zestflow.executor.lifecycle.ChainStateMachine;
import com.zestflow.executor.registry.ExecutorProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final DagSorter dagSorter;
    private final NodeRunner nodeRunner;
    private final ChainInstanceManager instanceManager;
    private final EventPublisher eventPublisher;
    private final InterceptorChain interceptorChain;
    private final ExecutorProperties properties;

    /** 并行执行线程池 */
    private final ForkJoinPool forkJoinPool = new ForkJoinPool(
            Math.min(Runtime.getRuntime().availableProcessors() * 2, 16));

    public DefaultChainExecutionEngine(ChainManager chainManager, DagSorter dagSorter,
                                       NodeRunner nodeRunner, ChainInstanceManager instanceManager,
                                       EventPublisher eventPublisher, InterceptorChain interceptorChain,
                                       ExecutorProperties properties) {
        this.chainManager = chainManager;
        this.dagSorter = dagSorter;
        this.nodeRunner = nodeRunner;
        this.instanceManager = instanceManager;
        this.eventPublisher = eventPublisher;
        this.interceptorChain = interceptorChain;
        this.properties = properties;
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
    public ChainExecuteResultDTO execute(String chainCode, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        log.info("链执行开始 chainCode={}", chainCode);

        // 1. 获取链定义
        ChainDefinition definition = chainManager.get(chainCode);
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

            // 5. 逐层执行
            for (int layerIndex = 0; layerIndex < layers.size(); layerIndex++) {
                List<String> layerNodeIds = layers.get(layerIndex);
                log.debug("链执行第 {} 层 chainCode={} nodes={}", layerIndex + 1, chainCode, layerNodeIds);

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

                List<NodeResultDTO> layerResults = executeLayer(layerNodeIds, definition, context, instance);
                allNodeResults.addAll(layerResults);

                boolean hasFailed = layerResults.stream()
                        .anyMatch(r -> r.getStatus() == ChainConstants.NODE_FAILED);
                if (hasFailed && ChainConstants.ERROR_STRATEGY_STOP.equals(definition.getErrorStrategy())) {
                    log.warn("节点执行失败，终止链执行 chainCode={}", chainCode);
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

            return ChainExecuteResultDTO.builder()
                    .instanceId(instance.getInstanceId())
                    .chainCode(chainCode)
                    .status(stateMachine.current())
                    .costMs(costMs)
                    .resultData(context.snapshot())
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
    public CompletableFuture<ChainExecuteResultDTO> executeAsync(String chainCode, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> execute(chainCode, params), forkJoinPool);
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
        String instanceId = instance.getInstanceId();
        eventPublisher.publish(ChainEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .executionId(instanceId)
                .chainId(instanceId)
                .chainName(chainCode)
                .executorId(properties.getModuleCode() + "@" + properties.getHost() + ":" + properties.getPort())
                .appName(properties.getModuleName())
                .timestamp(System.currentTimeMillis())
                .costMs(instance.elapsed())
                .status(eventType == ChainEvent.EventType.CHAIN_COMPLETED ? 1 : 0)
                .build());
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
}
