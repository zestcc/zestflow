package com.zestflow.test.playground.controller;

import com.zestflow.common.model.Result;
import com.zestflow.test.playground.dto.PaymentFlowRequest;
import com.zestflow.test.playground.dto.PaymentFlowResponse;
import com.zestflow.test.playground.service.PaymentFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 支付流转控制器 — 生产级 REST API
 * <p>
 * 对外暴露支付处理接口，屏蔽底层编排引擎细节。
 */
@Slf4j
@RestController
@RequestMapping("/api/playground/payment")
@RequiredArgsConstructor
public class PaymentFlowController {

    private final PaymentFlowService paymentFlowService;

    /**
     * 处理支付
     * <p>
     * 入口参数为业务 DTO，经过 ZestFlow 引擎完成完整编排后返回业务响应。
     */
    @PostMapping("/process")
    public Result<PaymentFlowResponse> processPayment(@Valid @RequestBody PaymentFlowRequest request) {
        log.info("收到支付请求 orderId={} amount={} {} payer={} payee={}",
                request.getOrderId(), request.getAmount(), request.getCurrency(),
                request.getPayerAccount(), request.getPayeeAccount());

        PaymentFlowResponse response = paymentFlowService.processPayment(request);

        log.info("支付处理完成 transactionId={} status={} costMs={}",
                response.getTransactionId(), response.getStatus(), response.getCostMs());

        return Result.success(response);
    }
}
