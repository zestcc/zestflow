package com.zestflow.demo.service;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.*;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainRuntimeRegistrar;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 业务编排服务 — 封装「业务场景 → 链定义 → 执行引擎」全流程。
 * <p>
 * 每个方法：
 * 1. 根据业务参数构建 ChainNodeDTO 列表
 * 2. 调用 chainDefinitionBuilder.build() 构建 ChainDefinition
 * 3. 调用 chainManager.load() 注册链
 * 4. 调用 chainExecutionEngine.execute() 执行
 * 5. 从结果中提取业务数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BizOrchestrationService {

    private final ChainDefinitionBuilder chainDefinitionBuilder;
    private final ChainManager chainManager;
    private final ChainExecutionEngine chainExecutionEngine;
    private final ChainRuntimeRegistrar chainRuntimeRegistrar;

    // ==================== 基础工具 ====================

    /**
     * 构建 NORMAL 节点
     */
    public static ChainNodeDTO normalNode(String id, String component) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component).build();
    }

    /**
     * 构建 NORMAL 节点（含配置）
     */
    public static ChainNodeDTO normalNode(String id, String component, Map<String, Object> config) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component).config(config).build();
    }

    /** 带前置/后置/参数校验器的 NORMAL 节点（生命周期配置写在 DTO 字段，非 config 内嵌） */
    public static ChainNodeDTO normalNodeWithLifecycle(String id, String component,
                                                       List<String> preProcessorIds,
                                                       List<String> postProcessorIds,
                                                       String paramValidatorId) {
        ChainNodeDTO.ChainNodeDTOBuilder builder = ChainNodeDTO.builder()
                .id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component);
        if (preProcessorIds != null && !preProcessorIds.isEmpty()) {
            builder.preComponents(preProcessorIds.stream()
                    .map(pid -> ComponentRef.builder().componentId(pid).build())
                    .toList());
        }
        if (postProcessorIds != null && !postProcessorIds.isEmpty()) {
            builder.postComponents(postProcessorIds.stream()
                    .map(pid -> ComponentRef.builder().componentId(pid).build())
                    .toList());
        }
        if (paramValidatorId != null && !paramValidatorId.isBlank()) {
            builder.paramValidator(ComponentRef.builder().componentId(paramValidatorId).build());
        }
        return builder.build();
    }

    public static ChainNodeDTO normalNodeWithResolvers(String id, String component, List<String> resolverIds) {
        ChainNodeDTO.ChainNodeDTOBuilder builder = ChainNodeDTO.builder()
                .id(id).label(id).type(ChainConstants.NODE_TYPE_NORMAL).component(component);
        if (resolverIds != null && !resolverIds.isEmpty()) {
            builder.paramResolvers(resolverIds.stream()
                    .map(rid -> ComponentRef.builder().componentId(rid).build())
                    .toList());
        }
        return builder.build();
    }

    /**
     * 构建 CONDITION 节点
     */
    public static ChainNodeDTO conditionNode(String id, Map<String, Object> config) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_CONDITION).config(config).build();
    }

    /**
     * 构建 SCRIPT 节点
     */
    public static ChainNodeDTO scriptNode(String id, String script) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_SCRIPT).script(script).build();
    }

    /**
     * 构建 SUB_CHAIN 节点
     */
    public static ChainNodeDTO subChainNode(String id, String subChainCode) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_SUB_CHAIN).subChainCode(subChainCode).build();
    }

    /**
     * 构建 ITERATOR 节点
     */
    public static ChainNodeDTO iteratorNode(String id, Map<String, Object> config) {
        return ChainNodeDTO.builder().id(id).label(id).type(ChainConstants.NODE_TYPE_ITERATOR).config(config).build();
    }

    /**
     * 构建边
     */
    public static ChainEdgeDTO edge(String source, String target) {
        return ChainEdgeDTO.builder().source(source).target(target).build();
    }

    public static ChainEdgeDTO edge(String source, String target, String condition) {
        return ChainEdgeDTO.builder().source(source).target(target).condition(condition).build();
    }

    /**
     * 加载并执行链
     */
    public ChainExecuteResultDTO loadAndExecute(String code, List<ChainNodeDTO> nodes,
                                                 List<ChainEdgeDTO> edges,
                                                 Map<String, Object> params,
                                                 Map<String, Object> chainConfig) {
        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(code).version(1).nodes(nodes).edges(edges)
                .config(chainConfig != null ? chainConfig : Map.of())
                .build();
        chainManager.load(chainDefinitionBuilder.build(dto));
        chainRuntimeRegistrar.ensurePublished(code);
        log.info("链已加载 code={} nodes={} edges={}", code, nodes.size(), edges.size());
        return chainExecutionEngine.execute(code, params != null ? params : Map.of());
    }

    public ChainExecuteResultDTO loadAndExecute(String code, List<ChainNodeDTO> nodes,
                                                 List<ChainEdgeDTO> edges,
                                                 Map<String, Object> params) {
        return loadAndExecute(code, nodes, edges, params, Map.of());
    }

    /**
     * 从数据库加载链定义并执行（引擎内部自动处理加载）
     */
    public ChainExecuteResultDTO loadFromDbAndExecute(String chainCode, Map<String, Object> params) {
        return chainExecutionEngine.execute(chainCode, params != null ? params : Map.of());
    }

    // ==================== 订单场景链定义 ====================

    /**
     * 简单线性下单（S01）
     */
    public ChainExecuteResultDTO simpleCreateOrder(String code, Map<String, Object> params) {
        return loadAndExecute(code, List.of(
                normalNode("create", "createOrder"),
                normalNode("pay", "processPayment"),
                normalNode("done", "sendNotify")
        ), List.of(edge("create", "pay"), edge("pay", "done")), params);
    }

    /**
     * 脚本计算折扣（S04）
     */
    public ChainExecuteResultDTO scriptDiscount(String code, Map<String, Object> params) {
        return loadAndExecute(code, List.of(
                normalNode("getPrice", "calcDiscount"),
                scriptNode("calc", "let base = long(ctx.get('price')); ctx.put('discount', base * 0.8); seq.map('discounted', base * 0.8)"),
                normalNode("result", "sendNotify")
        ), List.of(edge("getPrice", "calc"), edge("calc", "result")), params);
    }

    /**
     * 子链发货（S05）
     */
    public ChainExecuteResultDTO subChainShip(String code, String subChainCode, Map<String, Object> subChainParams,
                                               Map<String, Object> params) {
        // 先注册子链
        loadAndExecute(subChainCode, List.of(
                normalNode("pack", "printWaybill"),
                normalNode("deliver", "deliveryConfirm")
        ), List.of(edge("pack", "deliver")), subChainParams);
        // 主链
        return loadAndExecute(code, List.of(
                normalNode("prepare", "createOrder"),
                subChainNode("ship", subChainCode),
                normalNode("done", "sendNotify")
        ), List.of(edge("prepare", "ship"), edge("ship", "done")), params);
    }

    /**
     * 并行支付+库存校验（M01）
     */
    public ChainExecuteResultDTO parallelVerify(String code, Map<String, Object> params) {
        return loadAndExecute(code, List.of(
                normalNode("start", "validateUser"),
                normalNode("payCheck", "verifySignature"),
                normalNode("stockCheck", "checkStock"),
                normalNode("done", "sendNotify")
        ), List.of(
                edge("start", "payCheck"), edge("start", "stockCheck"),
                edge("payCheck", "done"), edge("stockCheck", "done")
        ), params);
    }

    /**
     * 全链路订单生命周期（L01）
     */
    public ChainExecuteResultDTO fullOrderLifecycle(String code, Map<String, Object> params) {
        return loadAndExecute(code, List.of(
                normalNode("create", "createOrder"),
                normalNode("pay", "createPayment"),
                normalNode("verify", "verifySignature"),
                normalNode("stock", "checkStock"),
                normalNode("deduct", "deductStock"),
                normalNode("notify", "sendNotify"),
                normalNode("split", "splitAmount")
        ), List.of(
                edge("create", "pay"), edge("create", "verify"),
                edge("pay", "stock"), edge("verify", "stock"),
                edge("stock", "deduct"),
                edge("deduct", "notify"), edge("deduct", "split")
        ), params);
    }

    /**
     * 连续重试 + 降级（S10）
     */
    public ChainExecuteResultDTO retryWithFallback(String code, Map<String, Object> params,
                                                    int retryCount, String fallbackComponent) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("retryCount", retryCount);
        config.put("fallback", Map.of("component", fallbackComponent));
        return loadAndExecute(code, List.of(
                normalNode("start", "validateUser"),
                normalNode("process", "nonexistent_component", config)
        ), List.of(edge("start", "process")), params);
    }

    // ==================== 各种编排场景 ====================

    /**
     * 复杂 DAG 菱形（L07）
     */
    public ChainExecuteResultDTO diamondDag(String code, Map<String, Object> params) {
        return loadAndExecute(code, List.of(
                normalNode("A", "validateUser"),
                normalNode("B", "processPayment"),
                normalNode("C", "deductStock"),
                normalNode("D", "sendNotify"),
                normalNode("E", "printWaybill")
        ), List.of(
                edge("A", "B"), edge("A", "C"),
                edge("B", "D"), edge("C", "D"),
                edge("D", "E")
        ), params);
    }

    /**
     * 复杂 DAG W 形（L08）
     */
    public ChainExecuteResultDTO wShapeDag(String code, Map<String, Object> params) {
        return loadAndExecute(code, List.of(
                normalNode("A", "validateUser"),
                normalNode("B", "processPayment"),
                normalNode("C", "deductStock"),
                normalNode("D", "sendNotify"),
                normalNode("E", "printWaybill"),
                normalNode("F", "deliveryConfirm")
        ), List.of(
                edge("A", "B"), edge("A", "C"),
                edge("B", "D"), edge("C", "D"),
                edge("D", "E"), edge("D", "F"),
                edge("E", "F")
        ), params);
    }

    /**
     * 10 层上下文传递（L10）
     */
    public ChainExecuteResultDTO tenLayers(String code, Map<String, Object> params) {
        List<ChainNodeDTO> nodes = new ArrayList<>();
        List<ChainEdgeDTO> edges = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String id = "N" + i;
            nodes.add(normalNode(id, "noopStep"));
            if (i > 1) {
                edges.add(edge("N" + (i - 1), id));
            }
        }
        return loadAndExecute(code, nodes, edges, params);
    }

    /**
     * 长链 50 节点（L15）
     */
    public ChainExecuteResultDTO longChain50(String code, Map<String, Object> params) {
        List<ChainNodeDTO> nodes = new ArrayList<>();
        List<ChainEdgeDTO> edges = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String id = "N" + i;
            nodes.add(normalNode(id, "noopStep"));
            if (i > 1) {
                edges.add(edge("N" + (i - 1), id));
            }
        }
        return loadAndExecute(code, nodes, edges, params);
    }

    /**
     * 嵌套子链（L12）
     */
    public ChainExecuteResultDTO nestedSubChain(String code, Map<String, Object> params) {
        String sub1 = code + "-sub1";
        String sub2 = code + "-sub2";
        loadAndExecute(sub1, List.of(
                normalNode("sa", "printWaybill"),
                normalNode("sb", "deliveryConfirm")
        ), List.of(edge("sa", "sb")), params);
        loadAndExecute(sub2, List.of(
                normalNode("sc", "assignCourier"),
                normalNode("sd", "createDelivery")
        ), List.of(edge("sc", "sd")), params);
        return loadAndExecute(code, List.of(
                normalNode("start", "validateUser"),
                subChainNode("sub1", sub1),
                normalNode("mid", "processPayment"),
                subChainNode("sub2", sub2),
                normalNode("end", "deductStock")
        ), List.of(
                edge("start", "sub1"), edge("sub1", "mid"),
                edge("mid", "sub2"), edge("sub2", "end")
        ), params);
    }

    /**
     * 全类型混合 DAG（L14）
     */
    public ChainExecuteResultDTO allTypesMixed(String code, Map<String, Object> params) {
        // 先注册子链
        String subCode = code + "-sub";
        loadAndExecute(subCode, List.of(
                normalNode("sub-start", "printWaybill"),
                normalNode("sub-end", "deliveryConfirm")
        ), List.of(edge("sub-start", "sub-end")), params);
        return loadAndExecute(code, List.of(
                normalNode("start", "validateUser"),
                scriptNode("script", "ctx.put('msg', 'hello'); seq.map('ok', true)"),
                conditionNode("cond", Map.of("condition", "params.status == 'PASS'")),
                normalNode("pass", "processPayment"),
                subChainNode("sub", subCode),
                iteratorNode("iter", Map.of(
                        "dataSource", "items", "itemName", "item",
                        "subNodes", List.of(Map.of("id", "subA", "label", "subA",
                                "type", "NORMAL", "component", "noopStep"))
                )),
                normalNode("end", "sendNotify")
        ), List.of(
                edge("start", "script"),
                edge("script", "cond"),
                edge("cond", "pass", "${params.status} == 'PASS'"),
                edge("pass", "sub"),
                edge("sub", "iter"),
                edge("iter", "end")
        ), params);
    }
}
