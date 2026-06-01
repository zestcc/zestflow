package com.zestflow.test.playground.service;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.*;
import com.zestflow.executor.chain.ChainDefinitionBuilder;
import com.zestflow.executor.chain.ChainManager;
import com.zestflow.executor.engine.ChainExecutionEngine;
import com.zestflow.test.playground.dto.PaymentFlowRequest;
import com.zestflow.test.playground.dto.PaymentFlowResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 支付流转服务 — 生产级业务编排
 * <p>
 * 职责：
 * <ol>
 *   <li>构建链定义（节点 + 边 + 脚本 + 配置）</li>
 *   <li>注册链到 ChainManager</li>
 *   <li>调用执行引擎</li>
 *   <li>从执行结果组装业务响应</li>
 * </ol>
 * <p>
 * 链结构：
 * <pre>
 * N1(validatePayment — pre*2 + validator + execute)
 *   ↓
 * N2(calculateFees — SCRIPT Groovy)
 *   ↓
 * N3(submitTransaction — execute + post*2)
 *   ↓
 * N4(sendReceipt — execute)
 * </pre>
 */
@Slf4j
@Service
public class PaymentFlowService {

    private static final String CHAIN_CODE = "playground-payment-flow";

    private final ChainDefinitionBuilder chainDefinitionBuilder;
    private final ChainManager chainManager;
    private final ChainExecutionEngine engine;

    public PaymentFlowService(ChainDefinitionBuilder chainDefinitionBuilder,
                               ChainManager chainManager,
                               ChainExecutionEngine engine) {
        this.chainDefinitionBuilder = chainDefinitionBuilder;
        this.chainManager = chainManager;
        this.engine = engine;
    }

    /**
     * 处理支付 — 完整业务编排入口
     * <p>
     * 调用方可直接传入业务 DTO，无需关心链定义注册和执行细节。
     */
    public PaymentFlowResponse processPayment(PaymentFlowRequest request) {
        buildAndRegisterChain();

        ChainExecuteResultDTO result = engine.execute(CHAIN_CODE, request);

        return assembleResponse(result);
    }

    /**
     * 处理支付（高级模式）— 返回完整执行结果，供调用方深度控制
     */
    public ChainExecuteResultDTO processPaymentRaw(PaymentFlowRequest request) {
        buildAndRegisterChain();
        return engine.execute(CHAIN_CODE, request);
    }

    /**
     * 构建并注册链定义
     */
    private void buildAndRegisterChain() {
        if (chainManager.get(CHAIN_CODE) != null) {
            return;
        }

        List<ChainNodeDTO> nodes = buildNodes();
        List<ChainEdgeDTO> edges = buildEdges();

        ChainDefinitionDTO dto = ChainDefinitionDTO.builder()
                .code(CHAIN_CODE)
                .version(1)
                .nodes(nodes)
                .edges(edges)
                .config(Map.of(
                        "errorStrategy", ChainConstants.ERROR_STRATEGY_STOP,
                        "timeout", 30000
                ))
                .build();

        chainManager.load(chainDefinitionBuilder.build(dto));
        log.info("支付链已注册 code={} nodes={}", CHAIN_CODE, nodes.size());
    }

    /**
     * 构建 4 个节点涵盖完整生命周期
     */
    private List<ChainNodeDTO> buildNodes() {
        List<ChainNodeDTO> nodes = new ArrayList<>();

        // N1: 验证支付 — 前置*2 + 参数校验 + 执行
        nodes.add(ChainNodeDTO.builder()
                .id("validatePayment")
                .label("验证支付")
                .type("EXECUTOR")
                .component("validateAndEnrichPayment")
                .componentName("验证并丰富支付信息")
                .groupName("paymentFlow")
                .description("汇率查询 + 风控检查 → 参数校验 → 数据丰富")
                .paramValidator(new ComponentRef("paymentFlow.paymentRequestValidator", null))
                .preComponents(List.of(
                        new ComponentRef("paymentFlow.enrichFxRate", null),
                        new ComponentRef("paymentFlow.checkCompliance", null)
                ))
                .build());

        // N2: 计算费用 — SCRIPT Groovy
        nodes.add(ChainNodeDTO.builder()
                .id("calculateFees")
                .label("计算费用")
                .type("SCRIPT")
                .script(buildFeeScript())
                .description("Groovy 脚本计算手续费和总金额")
                .build());

        // N3: 提交流水 — 执行 + 后置*2
        nodes.add(ChainNodeDTO.builder()
                .id("submitTransaction")
                .label("提交交易")
                .type("EXECUTOR")
                .component("submitTransaction")
                .componentName("提交流水")
                .groupName("paymentFlow")
                .description("提交支付网关 → 账户扣款 → 审计日志")
                .postComponents(List.of(
                        new ComponentRef("paymentFlow.deductAccountBalance", null),
                        new ComponentRef("paymentFlow.createTransactionAudit", null)
                ))
                .build());

        // N4: 发送回执 — 执行
        nodes.add(ChainNodeDTO.builder()
                .id("sendReceipt")
                .label("发送回执")
                .type("EXECUTOR")
                .component("deliverReceipt")
                .componentName("发送回执")
                .groupName("paymentFlow")
                .description("构建回执并发送通知")
                .build());

        return nodes;
    }

    /**
     * 构建链边
     */
    private List<ChainEdgeDTO> buildEdges() {
        return List.of(
                ChainEdgeDTO.builder().source("validatePayment").target("calculateFees").build(),
                ChainEdgeDTO.builder().source("calculateFees").target("submitTransaction").build(),
                ChainEdgeDTO.builder().source("submitTransaction").target("sendReceipt").build()
        );
    }

    /**
     * 费用计算脚本 — Groovy
     * <p>
     * 费率表：
     * <ul>
     *   <li>BANK_TRANSFER: 2.5%</li>
     *   <li>CREDIT_CARD: 3.5%</li>
     *   <li>BALANCE: 0.5%</li>
     * </ul>
     */
    private String buildFeeScript() {
        return "groovy:" +
                "def method = params.get('paymentMethod');" +
                "def rate = method == 'CREDIT_CARD' ? 0.035d :" +
                "  (method == 'BALANCE' ? 0.005d : 0.025d);" +
                "def amount = params.get('amount');" +
                "def fee = amount.multiply(new java.math.BigDecimal(rate)).setScale(2, java.math.RoundingMode.HALF_UP);" +
                "def total = amount.add(fee);" +
                "ctx.put('fee', fee);" +
                "ctx.put('totalAmount', total);" +
                "ctx.put('status', 'FEE_CALCULATED');" +
                "fee";
    }

    /**
     * 从执行结果组装业务响应
     */
    private PaymentFlowResponse assembleResponse(ChainExecuteResultDTO result) {
        Map<String, Object> data = result.getResultData() != null ? result.getResultData() : Map.of();

        String status;
        String message;
        if (result.getStatus() == ChainConstants.CHAIN_SUCCESS) {
            status = "SUCCESS";
            message = "支付处理成功";
        } else if (result.getStatus() == ChainConstants.CHAIN_TIMEOUT) {
            status = "TIMEOUT";
            message = "支付处理超时";
        } else {
            status = "FAILED";
            message = result.getErrorMessage() != null ? result.getErrorMessage() : "支付处理失败";
        }

        List<String> trace = result.getNodeResults() != null
                ? result.getNodeResults().stream()
                    .map(n -> n.getNodeId() + ":" + (n.getStatus() == 1 ? "OK" : "FAIL"))
                    .toList()
                : List.of();

        return PaymentFlowResponse.builder()
                .transactionId(getStr(data, "transactionId"))
                .status(status)
                .message(message)
                .amount(getDec(data, "amount"))
                .currency(getStr(data, "currency"))
                .amountInUsd(getDec(data, "amountInUsd"))
                .fee(getDec(data, "fee"))
                .totalAmount(getDec(data, "totalAmount"))
                .fxRate(getDec(data, "fxRate"))
                .fxRateLabel(getStr(data, "fxRateLabel"))
                .complianceCheckId(getStr(data, "complianceCheckId"))
                .complianceStatus(getStr(data, "complianceStatus"))
                .accountBalanceAfter(getLong(data, "accountBalanceAfter"))
                .auditEntryId(getStr(data, "auditEntryId"))
                .receiptId(getStr(data, "receiptId"))
                .costMs(result.getCostMs())
                .executionTrace(trace)
                .build();
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }

    private static BigDecimal getDec(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof BigDecimal bd) return bd;
        if (val instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private static Long getLong(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        return null;
    }
}
