package com.zestflow.executor.engine;

import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainDefinition.ChainEdge;
import com.zestflow.executor.chain.NodeDefinition;
import lombok.extern.slf4j.Slf4j;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.*;

/**
 * DAG 拓扑排序器（Kahn 算法）
 * <p>
 * 将链定义中的节点和边转换为分层执行序列，同层节点可并行执行。
 * 支持条件边（edges[].condition）的动态路由。
 */
@Slf4j
public class DagSorter {

    /**
     * 执行拓扑排序
     *
     * @param definition 链定义
     * @return 分层执行序列，每层中的节点可并行执行
     */
    public List<List<String>> sort(ChainDefinition definition) {
        return kahnSort(
                definition.getNodes(),
                definition.getEdges(),
                definition.getAdjacency(),
                definition.getInDegree()
        );
    }

    /**
     * Kahn 算法：BFS + 入度表
     * <p>
     * 每轮将所有入度为 0 的节点取出作为同一层级，
     * 减少它们的后继节点的入度，重复直到所有节点处理完毕。
     */
    private List<List<String>> kahnSort(Map<String, NodeDefinition> nodes,
                                         List<ChainEdge> edges,
                                         Map<String, List<String>> adjacency,
                                         Map<String, Integer> inDegree) {
        // 复制入度表
        Map<String, Integer> degree = new HashMap<>(inDegree);
        List<List<String>> layers = new ArrayList<>();

        // 初始队列：所有入度为 0 的节点
        Queue<String> queue = new LinkedList<>();
        for (String nodeId : nodes.keySet()) {
            if (degree.getOrDefault(nodeId, 0) == 0) {
                queue.add(nodeId);
            }
        }

        if (queue.isEmpty() && !nodes.isEmpty()) {
            log.warn("DAG 中无入度为 0 的节点，可能存在环路");
        }

        while (!queue.isEmpty()) {
            int layerSize = queue.size();
            List<String> currentLayer = new ArrayList<>(layerSize);

            for (int i = 0; i < layerSize; i++) {
                String nodeId = queue.poll();
                currentLayer.add(nodeId);

                List<String> successors = adjacency.getOrDefault(nodeId, List.of());
                for (String successor : successors) {
                    int newDegree = degree.get(successor) - 1;
                    degree.put(successor, newDegree);
                    if (newDegree == 0) {
                        queue.add(successor);
                    }
                }
            }

            layers.add(currentLayer);
        }

        // 校验是否所有节点都处理了
        long processedCount = layers.stream().mapToInt(List::size).sum();
        if (processedCount < nodes.size()) {
            long remaining = nodes.size() - processedCount;
            log.warn("拓扑排序完成，但 {} 个节点因环路未被处理", remaining);
        }

        if (log.isDebugEnabled()) {
            log.debug("拓扑排序完成 nodes={} layers={} order={}",
                    nodes.size(), layers.size(), layers);
        }

        return layers;
    }

    /**
     * 根据条件边过滤当前节点可到达的后继节点
     * <p>
     * 运行时动态判断：对于有多条出边的节点，根据条件表达式确定走哪个分支。
     */
    public List<String> resolveReachableSuccessors(String nodeId,
                                                     ChainDefinition definition,
                                                     Map<String, Object> data) {
        if (!definition.getAdjacency().containsKey(nodeId)) {
            return List.of();
        }

        // 查找从该节点出发的所有边
        List<ChainEdge> outgoingEdges = definition.getEdges().stream()
                .filter(e -> e.getSource().equals(nodeId))
                .toList();

        if (outgoingEdges.isEmpty()) {
            return definition.getAdjacency().getOrDefault(nodeId, List.of());
        }

        // 检查是否有带条件的边
        boolean hasConditionalEdges = outgoingEdges.stream()
                .anyMatch(e -> e.getCondition() != null && !e.getCondition().isEmpty());

        if (!hasConditionalEdges) {
            // 无条件边，全部可达
            return definition.getAdjacency().getOrDefault(nodeId, List.of());
        }

        // 有条件边，根据运行时数据判断
        List<String> reachable = new ArrayList<>();
        for (ChainEdge edge : outgoingEdges) {
            if (edge.getCondition() == null || edge.getCondition().isEmpty()) {
                reachable.add(edge.getTarget());
            } else if (evaluateCondition(edge.getCondition(), data)) {
                reachable.add(edge.getTarget());
                break; // 只走第一个匹配的条件分支
            }
        }

        return reachable;
    }

    /**
     * 评估条件表达式
     * <p>
     * V1 仅支持简单的等于/不等于判断。
     * 后续可扩展为 SpEL / Groovy 表达式引擎。
     */
    private boolean evaluateCondition(String condition, Map<String, Object> data) {
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
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                bindings.put(entry.getKey(), entry.getValue());
            }
            Object result = engine.eval(expr, bindings);
            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.warn("条件表达式评估失败 condition={}", condition, e);
            return false;
        }
    }
}
