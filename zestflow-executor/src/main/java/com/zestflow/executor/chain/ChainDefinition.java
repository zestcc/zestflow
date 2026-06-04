package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.protocol.ChainTransactionConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 运行时链定义（由 graph_data JSON 解析后的 DAG 结构）
 * <p>
 * 包含节点列表、边列表、邻接表、拓扑排序结果等执行引擎所需的全部信息。
 */
@Data
@Builder
@AllArgsConstructor
public class ChainDefinition {

    /** 链编码 */
    private String code;

    /** 链版本号 */
    private int version;

    /** 所有节点（id → NodeDefinition） */
    private Map<String, NodeDefinition> nodes;

    /** 边列表 */
    private List<ChainEdge> edges;

    /** 入度表：节点 ID → 入度 */
    private Map<String, Integer> inDegree;

    /** 邻接表：节点 ID → 后继节点 ID 列表 */
    private Map<String, List<String>> adjacency;

    /** 前置节点表：节点 ID → 前驱节点 ID 列表 */
    private Map<String, List<String>> predecessors;

    /**
     * 拓扑排序后的分层执行序列
     * 每个元素是一层可并行执行的节点集合
     */
    private List<List<String>> topologicalLayers;

    /** 链超时时间（毫秒） */
    @Builder.Default
    private long timeout = ChainConstants.DEFAULT_CHAIN_TIMEOUT_MS;

    /** 同层并行节点数上限 */
    @Builder.Default
    private int parallelThreshold = ChainConstants.DEFAULT_PARALLEL_THRESHOLD;

    /** 失败策略：STOP / CONTINUE / COMPENSATE */
    @Builder.Default
    private String errorStrategy = ChainConstants.ERROR_STRATEGY_STOP;

    /** 是否启用链路追踪 */
    @Builder.Default
    private boolean traceEnabled = false;

    /** 额外配置 */
    @Builder.Default
    private Map<String, Object> extraConfig = new HashMap<>();

    /** 链级 Spring 事务（设计器 config.transaction） */
    @Builder.Default
    private ChainTransactionConfig transactionConfig = ChainTransactionConfig.disabled();

    // ==================== 便捷查询方法 ====================

    public boolean isTransactionEnabled() {
        return transactionConfig != null && transactionConfig.isEnabled();
    }

    /**
     * 获取节点
     */
    public NodeDefinition getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 获取某节点的后继节点
     */
    public List<NodeDefinition> getSuccessors(String nodeId) {
        return adjacency.getOrDefault(nodeId, List.of())
                .stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取某节点的前驱节点
     */
    public List<NodeDefinition> getPredecessors(String nodeId) {
        return predecessors.getOrDefault(nodeId, List.of())
                .stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 获取拓扑层级数
     */
    public int layerCount() {
        return topologicalLayers != null ? topologicalLayers.size() : 0;
    }

    /**
     * 获取指定层级的节点
     */
    public List<NodeDefinition> getLayer(int index) {
        if (topologicalLayers == null || index < 0 || index >= topologicalLayers.size()) {
            return List.of();
        }
        return topologicalLayers.get(index)
                .stream()
                .map(nodes::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * 节点总数
     */
    public int nodeCount() {
        return nodes != null ? nodes.size() : 0;
    }

    // ==================== 内部类 ====================

    /**
     * 运行时边定义
     */
    @Data
    @AllArgsConstructor
    public static class ChainEdge {
        private String source;
        private String target;
        private String label;
        private String condition;
    }
}
