package com.zestflow.executor.chain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.protocol.ChainTransactionConfig;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainEdgeDTO;
import com.zestflow.common.model.dto.ChainNodeDTO;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 链定义构建器：将 {@link ChainDefinitionDTO} 或 graph_data JSON 解析为运行时 {@link ChainDefinition}。
 * <p>
 * 包含 DAG 拓扑排序（Kahn 算法）、边表 → 邻接表转换、配置合并等。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainDefinitionBuilder {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final ComponentScanner componentScanner;

    /**
     * 从 DTO 构建运行时 ChainDefinition
     */
    public ChainDefinition build(ChainDefinitionDTO dto) {
        Objects.requireNonNull(dto, "ChainDefinitionDTO 不能为空");
        Objects.requireNonNull(dto.getCode(), "链编码不能为空");

        // 1. 构建节点定义
        Map<String, NodeDefinition> nodeMap = buildNodes(dto);

        // 2. 构建边列表和邻接表
        List<ChainDefinition.ChainEdge> edges = buildEdges(dto, nodeMap);
        Map<String, List<String>> adjacency = buildAdjacency(edges);
        Map<String, Integer> inDegree = buildInDegree(nodeMap, adjacency);
        Map<String, List<String>> predecessors = buildPredecessors(edges);

        // 3. 拓扑排序（Kahn 算法）
        List<List<String>> topologicalLayers = topologicalSort(nodeMap, adjacency, inDegree);

        // 4. 解析链级别配置
        long chainTimeout = parseLongConfig(dto.getConfig(), "timeout", ChainConstants.DEFAULT_CHAIN_TIMEOUT_MS);
        int parallelThreshold = parseIntConfig(dto.getConfig(), "parallelThreshold", ChainConstants.DEFAULT_PARALLEL_THRESHOLD);
        String errorStrategy = parseStringConfig(dto.getConfig(), "errorStrategy", ChainConstants.ERROR_STRATEGY_STOP);
        boolean traceEnabled = parseBoolConfig(dto.getConfig(), "traceEnabled", false);
        ChainTransactionConfig transactionConfig = ChainTransactionConfig.fromExtraConfig(dto.getConfig());

        // 5. 构建最终 ChainDefinition
        ChainDefinition definition = ChainDefinition.builder()
                .code(dto.getCode())
                .version(dto.getVersion() != null ? dto.getVersion() : 1)
                .nodes(nodeMap)
                .edges(edges)
                .inDegree(inDegree)
                .adjacency(adjacency)
                .predecessors(predecessors)
                .topologicalLayers(topologicalLayers)
                .timeout(chainTimeout)
                .parallelThreshold(parallelThreshold)
                .errorStrategy(errorStrategy)
                .traceEnabled(traceEnabled)
                .transactionConfig(transactionConfig)
                .extraConfig(dto.getConfig() != null ? dto.getConfig() : Map.of())
                .build();

        log.info("链定义构建完成 code={} nodes={} layers={} version={}",
                definition.getCode(), definition.nodeCount(), definition.layerCount(), definition.getVersion());

        return definition;
    }

    /**
     * 从 JSON 字符串构建（优先使用 chainData，fallback 到 graphDataJson）
     */
    public ChainDefinition build(String chainCode, Integer version, String chainDataJson, String graphDataJson) {
        if (chainDataJson != null && !chainDataJson.isEmpty()) {
            try {
                ChainDefinitionDTO dto = MAPPER.readValue(chainDataJson, ChainDefinitionDTO.class);
                // 始终以 chainCode 参数为准（chainData JSON 中可能包含设计编码而非链编码）
                dto.setCode(chainCode);
                if (dto.getVersion() == null) {
                    dto.setVersion(version != null ? version : 1);
                }
                // chainData 仅有 entryNodeId 等元数据、无 nodes 时 fallback graphData（设计器未翻译场景）
                if (dto.getNodes() != null && !dto.getNodes().isEmpty()) {
                    return build(dto);
                }
                log.debug("chainData 无节点，fallback graphData code={}", chainCode);
            } catch (Exception e) {
                log.warn("chainData 解析失败，fallback graphData code={}", chainCode, e);
            }
        }
        return build(chainCode, version, graphDataJson);
    }

    /**
     * 从 graphData JSON 构建（兼容旧数据）
     */
    public ChainDefinition build(String chainCode, Integer version, String graphDataJson) {
        ChainDefinitionDTO dto = parseJson(chainCode, version, graphDataJson);
        return build(dto);
    }

    /**
     * 解析 JSON 为 DTO
     */
    public ChainDefinitionDTO parseJson(String chainCode, Integer version, String graphDataJson) {
        try {
            ChainDefinitionDTO dto = MAPPER.readValue(graphDataJson, ChainDefinitionDTO.class);
            // 始终以 chainCode 参数为准（graphData JSON 中可能包含设计编码而非链编码）
            dto.setCode(chainCode);
            if (dto.getVersion() == null) {
                dto.setVersion(version != null ? version : 1);
            }
            return dto;
        } catch (Exception e) {
            throw new IllegalArgumentException("链定义 JSON 解析失败 code=" + chainCode, e);
        }
    }

    // ==================== 私有构建方法 ====================

    private Map<String, NodeDefinition> buildNodes(ChainDefinitionDTO dto) {
        Map<String, NodeDefinition> nodeMap = new LinkedHashMap<>();
        if (dto.getNodes() == null) return nodeMap;

        for (ChainNodeDTO nodeDTO : dto.getNodes()) {
            nodeMap.put(nodeDTO.getId(), buildNode(nodeDTO));
        }
        return nodeMap;
    }

    /**
     * 构建单个节点定义（含递归：迭代器子节点）
     */
    private NodeDefinition buildNode(ChainNodeDTO nodeDTO) {
        Map<String, Object> cfg = nodeDTO.getConfig() != null ? nodeDTO.getConfig() : Map.of();

        NodeDefinition.NodeDefinitionBuilder builder = NodeDefinition.builder()
                .id(nodeDTO.getId())
                .label(nodeDTO.getLabel())
                .type(nodeDTO.getType() != null ? nodeDTO.getType() : ChainConstants.NODE_TYPE_NORMAL)
                .component(nodeDTO.getComponent())
                .componentName(nodeDTO.getComponentName())
                .groupName(nodeDTO.getGroupName())
                .description(nodeDTO.getDescription())
                .paramResolvers(nodeDTO.getParamResolvers())
                .paramValidator(nodeDTO.getParamValidator())
                .preComponents(nodeDTO.getPreComponents())
                .postComponents(nodeDTO.getPostComponents())
                .script(nodeDTO.getScript())
                .subChainCode(nodeDTO.getSubChainCode())
                .timeout(parseLongConfig(cfg, "timeout", ChainConstants.DEFAULT_NODE_TIMEOUT_MS))
                .retryCount(parseIntConfig(cfg, "retryCount", ChainConstants.DEFAULT_RETRY_COUNT))
                .retryInterval(parseLongConfig(cfg, "retryInterval", ChainConstants.DEFAULT_RETRY_INTERVAL_MS))
                .async(parseBoolConfig(cfg, "async", false))
                .condition(parseStringConfig(cfg, "condition", ""))
                .predicateMode(parseStringConfig(cfg, "predicateMode", "bind"))
                .predicateScript(parseStringConfig(cfg, "predicateScript", ""))
                .trueLabel(parseStringConfig(cfg, "trueLabel", "True"))
                .falseLabel(parseStringConfig(cfg, "falseLabel", "False"))
                .transactionPropagation(parseOptionalStringConfig(cfg, "transactionPropagation"));

        // 重试相关
        Map<String, Object> retryCfg = parseMapConfig(cfg, "retry");
        if (!retryCfg.isEmpty()) {
            builder.retryCount(parseIntConfig(retryCfg, "count", builder.build().getRetryCount()));
            builder.retryInterval(parseLongConfig(retryCfg, "interval", builder.build().getRetryInterval()));
            builder.retryBackoff(parseDoubleConfig(retryCfg, "backoff", 1.0));
            builder.retryFor(new HashSet<>(parseStringListConfig(retryCfg, "retryFor")));
        }

        // 降级相关
        Map<String, Object> fallbackCfg = parseMapConfig(cfg, "fallback");
        if (!fallbackCfg.isEmpty()) {
            builder.fallbackComponent(parseStringConfig(fallbackCfg, "component", ""));
            builder.fallbackOn(new HashSet<>(parseStringListConfig(fallbackCfg, "on")));
        }

        // 补偿相关
        Map<String, Object> compensateCfg = parseMapConfig(cfg, "compensate");
        if (!compensateCfg.isEmpty()) {
            builder.compensateComponent(parseStringConfig(compensateCfg, "component", ""));
        } else {
            String directComp = parseStringConfig(cfg, "compensateComponent", "");
            if (!directComp.isEmpty()) {
                builder.compensateComponent(directComp);
            }
        }

        // 熔断相关
        Map<String, Object> cbCfg = parseMapConfig(cfg, "circuitBreaker");
        if (!cbCfg.isEmpty()) {
            builder.circuitBreakerEnabled(parseBoolConfig(cbCfg, "enabled", false));
            builder.circuitBreakerThreshold(parseIntConfig(cbCfg, "failureThreshold",
                    ChainConstants.DEFAULT_CIRCUIT_BREAKER_THRESHOLD));
            builder.circuitBreakerRecoveryMs(parseLongConfig(cbCfg, "recoveryMs",
                    ChainConstants.DEFAULT_CIRCUIT_BREAKER_RECOVERY_MS));
        }

        // 迭代器相关：递归构建子节点
        if (ChainConstants.NODE_TYPE_ITERATOR.equals(nodeDTO.getType())) {
            builder.iteratorDataSource(parseStringConfig(cfg, "dataSource", ""));
            builder.iteratorItemName(parseStringConfig(cfg, "itemName", ""));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subNodeMaps = cfg.containsKey("subNodes")
                    ? (List<Map<String, Object>>) cfg.get("subNodes") : null;
            if (subNodeMaps != null && !subNodeMaps.isEmpty()) {
                List<NodeDefinition> subNodes = subNodeMaps.stream()
                        .map(map -> MAPPER.convertValue(map, ChainNodeDTO.class))
                        .map(this::buildNode)
                        .collect(Collectors.toList());
                builder.iteratorSubNodes(subNodes);
            }
        }

        // DELAY 节点：优先读取 delayMs 配置，并为 watchdog 留出余量
        if (ChainConstants.NODE_TYPE_DELAY.equals(nodeDTO.getType())) {
            long delayMs = parseLongConfig(cfg, "delayMs", 50L);
            builder.timeout(Math.max(delayMs + 2_000L, 1_000L));
        }

        if (!cfg.isEmpty()) {
            builder.config(new java.util.HashMap<>(cfg));
        }

        return builder.build();
    }

    private List<ChainDefinition.ChainEdge> buildEdges(ChainDefinitionDTO dto, Map<String, NodeDefinition> nodeMap) {
        if (dto.getEdges() == null) return List.of();

        return dto.getEdges().stream()
                .filter(e -> nodeMap.containsKey(e.getSource()) && nodeMap.containsKey(e.getTarget()))
                .map(e -> new ChainDefinition.ChainEdge(
                        e.getSource(), e.getTarget(), e.getLabel(), e.getCondition()))
                .collect(Collectors.toList());
    }

    private Map<String, List<String>> buildAdjacency(List<ChainDefinition.ChainEdge> edges) {
        Map<String, List<String>> adj = new HashMap<>();
        for (ChainDefinition.ChainEdge edge : edges) {
            adj.computeIfAbsent(edge.getSource(), k -> new ArrayList<>()).add(edge.getTarget());
        }
        return adj;
    }

    private Map<String, Integer> buildInDegree(Map<String, NodeDefinition> nodes,
                                               Map<String, List<String>> adjacency) {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
        }
        for (Map.Entry<String, List<String>> entry : adjacency.entrySet()) {
            for (String target : entry.getValue()) {
                inDegree.merge(target, 1, Integer::sum);
            }
        }
        return inDegree;
    }

    private Map<String, List<String>> buildPredecessors(List<ChainDefinition.ChainEdge> edges) {
        Map<String, List<String>> pred = new HashMap<>();
        for (ChainDefinition.ChainEdge edge : edges) {
            pred.computeIfAbsent(edge.getTarget(), k -> new ArrayList<>()).add(edge.getSource());
        }
        return pred;
    }

    /**
     * Kahn 算法拓扑排序，返回分层执行序列
     * 每层中的节点可并行执行
     */
    private List<List<String>> topologicalSort(Map<String, NodeDefinition> nodes,
                                                Map<String, List<String>> adjacency,
                                                Map<String, Integer> inDegree) {
        // 复制入度表
        Map<String, Integer> degree = new HashMap<>(inDegree);
        List<List<String>> layers = new ArrayList<>();

        // 找入度为 0 的节点作为初始层
        Queue<String> queue = new LinkedList<>();
        for (String nodeId : nodes.keySet()) {
            if (degree.getOrDefault(nodeId, 0) == 0) {
                queue.add(nodeId);
            }
        }

        while (!queue.isEmpty()) {
            List<String> currentLayer = new ArrayList<>();
            int layerSize = queue.size();

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

        // 检查是否有节点未处理（环路）
        long remaining = degree.values().stream().filter(d -> d > 0).count();
        if (remaining > 0) {
            log.warn("拓扑排序检测到环，{} 个节点未被处理", remaining);
        }

        return layers;
    }

    // ==================== 配置解析工具 ====================

    @SuppressWarnings("unchecked")
    private static long parseLongConfig(Map<String, Object> config, String key, long defaultValue) {
        if (config == null) return defaultValue;
        Object val = config.get(key);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private static int parseIntConfig(Map<String, Object> config, String key, int defaultValue) {
        if (config == null) return defaultValue;
        Object val = config.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { /* ignore */ }
        }
        return defaultValue;
    }

    private static double parseDoubleConfig(Map<String, Object> config, String key, double defaultValue) {
        if (config == null) return defaultValue;
        Object val = config.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultValue;
    }

    private static boolean parseBoolConfig(Map<String, Object> config, String key, boolean defaultValue) {
        if (config == null) return defaultValue;
        Object val = config.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        if (val instanceof String) return Boolean.parseBoolean((String) val);
        return defaultValue;
    }

    private static String parseStringConfig(Map<String, Object> config, String key, String defaultValue) {
        if (config == null) return defaultValue;
        Object val = config.get(key);
        return val instanceof String ? (String) val : defaultValue;
    }

    private static String parseOptionalStringConfig(Map<String, Object> config, String key) {
        if (config == null) return null;
        Object val = config.get(key);
        if (val == null) return null;
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseStringListConfig(Map<String, Object> config, String key) {
        if (config == null) return List.of();
        Object val = config.get(key);
        if (val instanceof List) {
            return ((List<Object>) val).stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseMapConfig(Map<String, Object> config, String key) {
        if (config == null) return Map.of();
        Object val = config.get(key);
        if (val instanceof Map) return (Map<String, Object>) val;
        return Map.of();
    }
}
