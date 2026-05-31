package com.zestflow.test.controller;

import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.test.dto.OrderRequest;
import com.zestflow.test.dto.OrderResponse;
import com.zestflow.test.service.BizOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单中心 Controller — 模拟生产级订单 API
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final BizOrchestrationService orch;

    // ==================== S01: 简单线性下单 ====================

    @PostMapping("/create")
    public Result<OrderResponse> createOrder(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-create-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);
        var result = orch.simpleCreateOrder(chainCode, params);

        OrderResponse resp = OrderResponse.builder()
                .orderId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .totalAmount(req.getAmount())
                .costMs(result.getCostMs())
                .nodeResults(result.getNodeResults().stream().map(nr -> nr.getNodeId() + ":" + nr.getStatus()).collect(Collectors.toList()))
                .build();
        return Result.success(resp);
    }

    // ==================== S04: 脚本计算折扣 ====================

    @PostMapping("/discount")
    public Result<OrderResponse> calcDiscount(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-discount-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("price", req.getAmount());
        params.put("userId", req.getUserId());
        var result = orch.scriptDiscount(chainCode, params);

        OrderResponse resp = OrderResponse.builder()
                .orderId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .totalAmount(req.getAmount())
                .costMs(result.getCostMs())
                .resultData(result.getResultData())
                .build();
        return Result.success(resp);
    }

    // ==================== S05: 子链发货 ====================

    @PostMapping("/ship")
    public Result<OrderResponse> shipOrder(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-ship-" + UUID.randomUUID().toString().substring(0, 8);
        String subCode = chainCode + "-sub";
        Map<String, Object> params = buildOrderParams(req);
        var result = orch.subChainShip(chainCode, subCode, params, params);

        OrderResponse resp = OrderResponse.builder()
                .orderId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .costMs(result.getCostMs())
                .nodeResults(result.getNodeResults().stream().map(nr -> nr.getNodeId() + ":" + nr.getStatus()).collect(Collectors.toList()))
                .build();
        return Result.success(resp);
    }

    // ==================== M01: 并行支付+库存校验 ====================

    @PostMapping("/verify")
    public Result<OrderResponse> parallelVerify(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-verify-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);
        var result = orch.parallelVerify(chainCode, params);

        OrderResponse resp = OrderResponse.builder()
                .orderId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .costMs(result.getCostMs())
                .nodeResults(result.getNodeResults().stream().map(nr -> nr.getNodeId() + ":" + nr.getStatus()).collect(Collectors.toList()))
                .build();
        return Result.success(resp);
    }

    // ==================== M02: 多条件分支 ====================

    @PostMapping("/route")
    public Result<OrderResponse> routeByChannel(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-route-" + UUID.randomUUID().toString().substring(0, 8);
        String channel = req.getChannel() != null ? req.getChannel() : "APP";

        Map<String, Object> params = buildOrderParams(req);
        // 条件节点 + 分支节点
        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.conditionNode("gate", Map.of("condition", "params.channel == '" + channel + "'")),
                BizOrchestrationService.normalNode("matched", "biz002"),
                BizOrchestrationService.normalNode("unmatched", "biz003"),
                BizOrchestrationService.normalNode("end", "biz004")
        ), List.of(
                BizOrchestrationService.edge("start", "gate"),
                BizOrchestrationService.edge("gate", "matched", "${params.channel} == '" + channel + "'"),
                BizOrchestrationService.edge("gate", "unmatched"),
                BizOrchestrationService.edge("matched", "end"),
                BizOrchestrationService.edge("unmatched", "end")
        ), params);

        OrderResponse resp = OrderResponse.builder()
                .orderId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .channel(channel)
                .costMs(result.getCostMs())
                .nodeResults(result.getNodeResults().stream().map(nr -> nr.getNodeId() + ":" + nr.getStatus()).collect(Collectors.toList()))
                .build();
        return Result.success(resp);
    }

    // ==================== M04: 预处理器 ====================

    @PostMapping("/with-preprocessor")
    public Result<OrderResponse> withPreprocessor(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-pre-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("process", "biz001", Map.of(
                        "preComponents", List.of(Map.of("component", "preprocessor.preProc001"))
                ))
        ), List.of(), params);

        return Result.success(OrderResponse.builder()
                .orderId(result.getInstanceId()).status(statusText(result.getStatus())).costMs(result.getCostMs()).build());
    }

    // ==================== M05: 后处理器 ====================

    @PostMapping("/with-postprocessor")
    public Result<OrderResponse> withPostprocessor(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-post-" + UUID.randomUUID().toString().substring(0, 8);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("process", "biz001", Map.of(
                        "postComponents", List.of(Map.of("component", "postprocessor.postProc001"))
                ))
        ), List.of(), buildOrderParams(req));

        return Result.success(OrderResponse.builder()
                .orderId(result.getInstanceId()).status(statusText(result.getStatus())).costMs(result.getCostMs()).build());
    }

    // ==================== M07: 参数校验器 ====================

    @PostMapping("/validate")
    public Result<OrderResponse> withValidator(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-validate-" + UUID.randomUUID().toString().substring(0, 8);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("process", "biz001", Map.of(
                        "paramValidator", Map.of("component", "paramvalidator.validate001")
                ))
        ), List.of(), buildOrderParams(req));

        return Result.success(OrderResponse.builder()
                .orderId(result.getInstanceId()).status(statusText(result.getStatus())).costMs(result.getCostMs()).build());
    }

    // ==================== L01: 全链路订单生命周期 ====================

    @PostMapping("/full-lifecycle")
    public Result<OrderResponse> fullLifecycle(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-full-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);
        var result = orch.fullOrderLifecycle(chainCode, params);

        OrderResponse.FullLifecycleDetail detail = OrderResponse.FullLifecycleDetail.builder()
                .orderId(result.getInstanceId())
                .payResult(getResultData(result, "pay"))
                .verifyResult(getResultData(result, "verify"))
                .stockResult(getResultData(result, "stock"))
                .deductResult(getResultData(result, "deduct"))
                .notifyResult(getResultData(result, "notify"))
                .splitResult(getResultData(result, "split"))
                .build();

        OrderResponse resp = OrderResponse.builder()
                .orderId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .totalAmount(req.getAmount())
                .costMs(result.getCostMs())
                .nodeResults(result.getNodeResults().stream().map(nr -> nr.getNodeId() + ":" + nr.getStatus()).collect(Collectors.toList()))
                .fullDetail(detail)
                .build();
        return Result.success(resp);
    }

    // ==================== L12: 嵌套子链 ====================

    @PostMapping("/nested-subchain")
    public Result<OrderResponse> nestedSubchain(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-nested-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);
        var result = orch.nestedSubChain(chainCode, params);

        return Result.success(OrderResponse.builder()
                .orderId(result.getInstanceId()).status(statusText(result.getStatus())).costMs(result.getCostMs())
                .nodeResults(result.getNodeResults().stream().map(nr -> nr.getNodeId() + ":" + nr.getStatus()).collect(Collectors.toList()))
                .build());
    }

    // ==================== E02: 元件不存在 ====================

    @PostMapping("/bad-component")
    public Result<OrderResponse> badComponent(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-bad-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("good", "biz001"),
                BizOrchestrationService.normalNode("bad", "nonexistent_component"),
                BizOrchestrationService.normalNode("never", "biz003")
        ), List.of(
                BizOrchestrationService.edge("good", "bad"),
                BizOrchestrationService.edge("bad", "never")
        ), params);

        return Result.success(OrderResponse.builder()
                .orderId(result.getInstanceId()).status(statusText(result.getStatus()))
                .errorMessage(result.getErrorMessage())
                .costMs(result.getCostMs()).build());
    }

    // ==================== E09: JSON 大参数 ====================

    @PostMapping("/large-params")
    public Result<OrderResponse> largeParams(@Valid @RequestBody OrderRequest req) {
        String chainCode = "order-large-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = buildOrderParams(req);
        // 模拟 10KB 入参
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("data").append(i).append(",");
        params.put("largeField", sb.toString());

        var result = orch.simpleCreateOrder(chainCode, params);
        return Result.success(OrderResponse.builder()
                .orderId(result.getInstanceId()).status(statusText(result.getStatus())).costMs(result.getCostMs()).build());
    }

    // ==================== 辅助 ====================

    private Map<String, Object> buildOrderParams(OrderRequest req) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("userId", req.getUserId());
        params.put("productId", req.getProductId());
        params.put("quantity", req.getQuantity());
        params.put("amount", req.getAmount());
        params.put("payMethod", req.getPayMethod() != null ? req.getPayMethod() : "WECHAT");
        params.put("channel", req.getChannel() != null ? req.getChannel() : "APP");
        if (req.getItems() != null && !req.getItems().isEmpty()) {
            params.put("items", req.getItems());
        }
        return params;
    }

    private String getResultData(ChainExecuteResultDTO result, String prefix) {
        if (result.getResultData() == null) return null;
        Object val = result.getResultData().get(prefix + "Result");
        return val != null ? val.toString() : result.getResultData().getOrDefault(prefix, "N/A").toString();
    }

    private String statusText(int status) {
        switch (status) {
            case 4: return "SUCCESS";
            case 5: return "FAILED";
            case 6: return "TIMEOUT";
            default: return "UNKNOWN";
        }
    }
}
