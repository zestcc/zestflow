package com.zestflow.executor.chain;

import com.zestflow.executor.scanner.ComponentScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 链定义校验器
 * <p>
 * 校验项：
 * <ul>
 *   <li>DAG 有环检测（DFS 染色法）</li>
 *   <li>组件存在性校验（对照 {@link ComponentScanner} 注册表）</li>
 *   <li>配置合法性校验（超时、重试等参数）</li>
 *   <li>边引用完整性校验</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class ChainValidator {

    private final ComponentScanner componentScanner;

    /**
     * 校验单个链定义
     *
     * @return 校验错误信息列表（为空表示校验通过）
     */
    public List<String> validate(ChainDefinition definition) {
        List<String> errors = new ArrayList<>();

        if (definition == null) {
            errors.add("链定义为 null");
            return errors;
        }

        String code = definition.getCode();
        if (code == null || code.isEmpty()) {
            errors.add("链编码不能为空");
        }

        if (definition.nodeCount() == 0) {
            errors.add("链[" + code + "] 没有节点");
        }

        // 1. 校验边引用完整性
        validateEdgeReferences(definition, errors);

        // 2. 检测 DAG 环路
        detectCycle(definition, errors);

        // 3. 校验组件存在性
        validateComponents(definition, errors);

        // 4. 校验配置
        validateConfig(definition, errors);

        if (errors.isEmpty()) {
            log.debug("链定义校验通过 code={} nodes={}", code, definition.nodeCount());
        } else {
            log.warn("链定义校验失败 code={} errors={}", code, errors);
        }

        return errors;
    }

    /**
     * 校验所有链定义
     *
     * @return 是否全部通过
     */
    public boolean validateAll(List<ChainDefinition> definitions) {
        boolean allPass = true;
        for (ChainDefinition def : definitions) {
            List<String> errors = validate(def);
            if (!errors.isEmpty()) {
                allPass = false;
            }
        }
        return allPass;
    }

    /**
     * 快速判断是否有环
     */
    public boolean hasCycle(ChainDefinition definition) {
        List<String> errors = new ArrayList<>();
        detectCycle(definition, errors);
        return !errors.isEmpty();
    }

    // ==================== 私有校验方法 ====================

    private void validateEdgeReferences(ChainDefinition def, List<String> errors) {
        if (def.getEdges() == null) return;

        for (ChainDefinition.ChainEdge edge : def.getEdges()) {
            if (!def.getNodes().containsKey(edge.getSource())) {
                errors.add("边引用了不存在的源节点: " + edge.getSource());
            }
            if (!def.getNodes().containsKey(edge.getTarget())) {
                errors.add("边引用了不存在的目标节点: " + edge.getTarget());
            }
        }
    }

    /**
     * DFS 染色法检测有向环
     * WHITE(0)=未访问, GRAY(1)=访问中, BLACK(2)=已访问
     */
    private void detectCycle(ChainDefinition def, List<String> errors) {
        Map<String, Integer> color = new HashMap<>();
        for (String nodeId : def.getNodes().keySet()) {
            color.put(nodeId, 0); // WHITE
        }

        List<String> path = new ArrayList<>();
        for (String nodeId : def.getNodes().keySet()) {
            if (color.get(nodeId) == 0) {
                if (dfsHasCycle(nodeId, def.getAdjacency(), color, path)) {
                    errors.add("链[" + def.getCode() + "] 存在环路，路径: " + String.join(" → ", path));
                    return;
                }
            }
        }
    }

    private boolean dfsHasCycle(String nodeId, Map<String, List<String>> adjacency,
                                Map<String, Integer> color, List<String> path) {
        color.put(nodeId, 1); // GRAY
        path.add(nodeId);

        List<String> successors = adjacency.getOrDefault(nodeId, List.of());
        for (String next : successors) {
            if (color.getOrDefault(next, 0) == 1) {
                // 发现回边 → 有环
                path.add(next);
                return true;
            }
            if (color.getOrDefault(next, 0) == 0) {
                if (dfsHasCycle(next, adjacency, color, path)) {
                    return true;
                }
            }
        }

        color.put(nodeId, 2); // BLACK
        path.remove(path.size() - 1);
        return false;
    }

    private void validateComponents(ChainDefinition def, List<String> errors) {
        for (NodeDefinition node : def.getNodes().values()) {
            if (node.isNormal() || node.isCondition()) {
                if (node.getComponent() == null || node.getComponent().isEmpty()) {
                    errors.add("节点[" + node.getId() + "] 缺少 component 配置");
                } else if (componentScanner.getComponent(node.getComponent()) == null) {
                    errors.add("节点[" + node.getId() + "] 引用了不存在的组件: " + node.getComponent()
                            + "，可用组件: " + componentScanner.getComponentIds());
                }
            }
        }
    }

    private void validateConfig(ChainDefinition def, List<String> errors) {
        for (NodeDefinition node : def.getNodes().values()) {
            if (node.getTimeout() < 0 && node.getTimeout() != -1) {
                errors.add("节点[" + node.getId() + "] 超时时间不能为负数");
            }
            if (node.getRetryCount() < 0) {
                errors.add("节点[" + node.getId() + "] 重试次数不能为负数");
            }
            if (node.getType() == null || node.getType().isEmpty()) {
                errors.add("节点[" + node.getId() + "] 类型不能为空");
            }
        }
    }
}
