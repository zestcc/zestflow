package com.zestflow.executor.interceptor;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 指标拦截器 — 记录链/节点执行次数和耗时
 */
@Slf4j
public class MetricsInterceptor implements ChainInterceptor, NodeInterceptor {

    private static final int MAX_CHAIN_METRICS = 512;
    private static final int MAX_NODE_METRICS = 2048;

    /** 链级指标 */
    private final Map<String, ChainMetrics> chainMetrics = new ConcurrentHashMap<>();

    /** 节点级指标 */
    private final Map<String, NodeMetrics> nodeMetrics = new ConcurrentHashMap<>();

    private static class ChainMetrics {
        final LongAdder invokeCount = new LongAdder();
        final LongAdder successCount = new LongAdder();
        final LongAdder failCount = new LongAdder();
        final AtomicLong totalCostMs = new AtomicLong(0);
    }

    private static class NodeMetrics {
        final LongAdder invokeCount = new LongAdder();
        final LongAdder successCount = new LongAdder();
        final LongAdder failCount = new LongAdder();
        final LongAdder retryCount = new LongAdder();
        final LongAdder fallbackCount = new LongAdder();
        final AtomicLong totalCostMs = new AtomicLong(0);
    }

    // ==================== ChainInterceptor ====================

    @Override
    public void beforeChain(String chainCode, ChainContext ctx) {
        ensureChainMetric(chainCode).invokeCount.increment();
    }

    @Override
    public void afterChain(String chainCode, ChainContext ctx, List<?> nodeResults) {
        ChainMetrics metrics = chainMetrics.get(chainCode);
        if (metrics != null) {
            metrics.successCount.increment();
            metrics.totalCostMs.addAndGet(ctx.getElapsedMs());
        }
    }

    @Override
    public void onChainError(String chainCode, ChainContext ctx, Throwable e) {
        ChainMetrics metrics = chainMetrics.get(chainCode);
        if (metrics != null) {
            metrics.failCount.increment();
        }
    }

    // ==================== NodeInterceptor ====================

    @Override
    public void beforeNode(NodeDefinition node, ChainContext ctx) {
        ensureNodeMetric(node.getId()).invokeCount.increment();
    }

    @Override
    public void afterNode(NodeDefinition node, ChainContext ctx, Object result) {
        NodeMetrics metrics = nodeMetrics.get(node.getId());
        if (metrics != null) {
            metrics.successCount.increment();
        }
    }

    @Override
    public void onNodeError(NodeDefinition node, ChainContext ctx, Throwable e) {
        NodeMetrics metrics = nodeMetrics.get(node.getId());
        if (metrics != null) {
            metrics.failCount.increment();
        }
    }

    @Override
    public int order() {
        return 0;
    }

    // ==================== 指标查询 ====================

    private ChainMetrics ensureChainMetric(String chainCode) {
        evictIfNeeded(chainMetrics, MAX_CHAIN_METRICS, chainCode);
        return chainMetrics.computeIfAbsent(chainCode, k -> new ChainMetrics());
    }

    private NodeMetrics ensureNodeMetric(String nodeId) {
        evictIfNeeded(nodeMetrics, MAX_NODE_METRICS, nodeId);
        return nodeMetrics.computeIfAbsent(nodeId, k -> new NodeMetrics());
    }

    private static <K> void evictIfNeeded(Map<K, ?> map, int maxSize, K key) {
        if (map.size() < maxSize || map.containsKey(key)) {
            return;
        }
        var iterator = map.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * 打印链指标
     */
    public void printChainMetrics(String chainCode) {
        ChainMetrics m = chainMetrics.get(chainCode);
        if (m == null) {
            log.info("链指标 [{}] 无数据", chainCode);
            return;
        }
        log.info("链指标 [{}] 调用={} 成功={} 失败={} 总耗时={}ms",
                chainCode, m.invokeCount.sum(), m.successCount.sum(),
                m.failCount.sum(), m.totalCostMs.get());
    }

    /**
     * 打印节点指标
     */
    public void printNodeMetrics(String nodeId) {
        NodeMetrics m = nodeMetrics.get(nodeId);
        if (m == null) {
            log.info("节点指标 [{}] 无数据", nodeId);
            return;
        }
        log.info("节点指标 [{}] 调用={} 成功={} 失败={} 重试={} 降级={}",
                nodeId, m.invokeCount.sum(), m.successCount.sum(),
                m.failCount.sum(), m.retryCount.sum(), m.fallbackCount.sum());
    }
}
