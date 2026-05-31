package com.zestflow.test.controller;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.test.dto.PaymentRequest;
import com.zestflow.test.dto.PaymentResponse;
import com.zestflow.test.service.BizOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付中心 Controller — 支付处理/审批/重试/降级/熔断
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final BizOrchestrationService orch;

    // ==================== S02: 快速支付 ====================

    @PostMapping("/quick")
    public Result<PaymentResponse> quickPay(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-quick-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = payParams(req);
        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("pay", "processPayment"),
                BizOrchestrationService.normalNode("done", "biz004")
        ), List.of(
                BizOrchestrationService.edge("pay", "done")
        ), params);

        return Result.success(baseResponse(result, req));
    }

    // ==================== S07: 条件-小额通过 ====================

    @PostMapping("/approval")
    public Result<PaymentResponse> approval(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-approval-" + UUID.randomUUID().toString().substring(0, 8);
        int amount = req.getApprovalAmount() != null ? req.getApprovalAmount() : req.getAmount();
        Map<String, Object> params = payParams(req);
        params.put("amount", amount);

        boolean isSmall = amount < 5000;
        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.conditionNode("cond", Map.of("condition", "params.amount < 5000")),
                BizOrchestrationService.normalNode("approved", "biz002"),
                BizOrchestrationService.normalNode("rejected", "biz003"),
                BizOrchestrationService.normalNode("end", "biz004")
        ), List.of(
                BizOrchestrationService.edge("start", "cond"),
                BizOrchestrationService.edge("cond", "approved", "${params.amount} < 5000"),
                BizOrchestrationService.edge("cond", "rejected"),
                BizOrchestrationService.edge("approved", "end"),
                BizOrchestrationService.edge("rejected", "end")
        ), params);

        boolean approved = result.getNodeResults().stream().anyMatch(nr -> "approved".equals(nr.getNodeId()) && nr.getStatus() == 3);
        PaymentResponse resp = baseResponse(result, req);
        resp.setApprovalResult(approved ? "APPROVED" : "REJECTED");
        return Result.success(resp);
    }

    // ==================== S09: 重试成功 ====================

    @PostMapping("/retryable")
    public Result<PaymentResponse> retryablePay(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-retry-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = payParams(req);
        params.put("simulateFailCount", req.getSimulateFailCount() > 0 ? req.getSimulateFailCount() : 1);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.normalNode("pay", "processPayment", Map.of("retryCount", 3, "retryInterval", 10))
        ), List.of(
                BizOrchestrationService.edge("start", "pay")
        ), params);

        PaymentResponse resp = baseResponse(result, req);
        return Result.success(resp);
    }

    // ==================== S10: 降级兜底 ====================

    @PostMapping("/fallback")
    public Result<PaymentResponse> fallbackPay(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-fallback-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = payParams(req);

        var result = orch.retryWithFallback(chainCode, params, 2, "fallback001");

        PaymentResponse resp = baseResponse(result, req);
        resp.setFallbackUsed(result.getNodeResults().stream()
                .anyMatch(nr -> nr.getStatus() == ChainConstants.NODE_FALLBACKING || nr.getNodeId().contains("fallback")));
        return Result.success(resp);
    }

    // ==================== L02: 熔断器-连续失败 ====================

    @PostMapping("/circuit-break")
    public Result<PaymentResponse> circuitBreak(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-cb-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = payParams(req);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.normalNode("risky", "nonexistent_component", Map.of(
                        "retryCount", 0,
                        "circuitBreaker", Map.of("enabled", true, "failureThreshold", 1, "recoveryMs", 50000)
                ))
        ), List.of(
                BizOrchestrationService.edge("start", "risky")
        ), params);

        PaymentResponse resp = baseResponse(result, req);
        resp.setCircuitBreakerOpen(true);
        return Result.success(resp);
    }

    // ==================== L03: 熔断器-恢复 ====================

    @PostMapping("/circuit-recover")
    public Result<PaymentResponse> circuitRecover(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-cr-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = payParams(req);

        // 第一次：触发熔断
        orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("risky", "nonexistent_component", Map.of(
                        "retryCount", 0,
                        "circuitBreaker", Map.of("enabled", true, "failureThreshold", 1, "recoveryMs", 100)
                ))
        ), List.of(), params);

        // 等待熔断器冷却后恢复正常
        try { Thread.sleep(150); } catch (InterruptedException ignored) {}

        // 第二次：应该恢复（半开 → 成功走生意元件）
        var result2 = orch.loadAndExecute(chainCode + "-v2", List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.normalNode("good", "biz002"),
                BizOrchestrationService.normalNode("done", "biz003")
        ), List.of(
                BizOrchestrationService.edge("start", "good"),
                BizOrchestrationService.edge("good", "done")
        ), params);

        PaymentResponse resp = baseResponse(result2, req);
        resp.setCircuitBreakerOpen(false);
        resp.setStatus("RECOVERED");
        return Result.success(resp);
    }

    // ==================== M06: 参数绑定器 ====================

    @PostMapping("/bind-params")
    public Result<PaymentResponse> bindParams(@Valid @RequestBody PaymentRequest req) {
        String chainCode = "pay-bind-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = payParams(req);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("process", "processPayment", Map.of(
                        "paramResolvers", List.of(Map.of("component", "parambinder.bindParam001"))
                ))
        ), List.of(), params);

        return Result.success(baseResponse(result, req));
    }

    // ==================== 辅助 ====================

    private Map<String, Object> payParams(PaymentRequest req) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderId", req.getOrderId());
        params.put("userId", req.getUserId());
        params.put("amount", req.getAmount());
        params.put("method", req.getMethod() != null ? req.getMethod() : "WECHAT");
        if (req.getSimulateFailCount() > 0) {
            params.put("simulateFailCount", req.getSimulateFailCount());
        }
        return params;
    }

    private PaymentResponse baseResponse(ChainExecuteResultDTO result, PaymentRequest req) {
        return PaymentResponse.builder()
                .paymentId(result.getInstanceId())
                .status(statusText(result.getStatus()))
                .amount(req.getAmount())
                .method(req.getMethod())
                .costMs(result.getCostMs())
                .errorMessage(result.getErrorMessage())
                .nodeSequence(result.getNodeResults().stream().map(nr -> nr.getNodeId()).collect(Collectors.toList()))
                .retryCount((int) result.getNodeResults().stream().filter(nr -> nr.getRetryCount() != null).mapToInt(nr -> nr.getRetryCount()).sum())
                .build();
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
