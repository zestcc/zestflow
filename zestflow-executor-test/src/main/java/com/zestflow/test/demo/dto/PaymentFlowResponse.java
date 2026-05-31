package com.zestflow.test.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付流转响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFlowResponse {

    /** 交易流水号 */
    private String transactionId;

    /** 业务状态码 */
    private String status;

    /** 状态描述 */
    private String message;

    /** 原始金额 */
    private BigDecimal amount;

    /** 币种 */
    private String currency;

    /** 折合 USD 金额 */
    private BigDecimal amountInUsd;

    /** 手续费 */
    private BigDecimal fee;

    /** 总扣款金额 */
    private BigDecimal totalAmount;

    /** 汇率 */
    private BigDecimal fxRate;

    /** 汇率描述 */
    private String fxRateLabel;

    /** 风控检查编号 */
    private String complianceCheckId;

    /** 风控结果 */
    private String complianceStatus;

    /** 扣款后余额 */
    private Long accountBalanceAfter;

    /** 审计日志编号 */
    private String auditEntryId;

    /** 回执编号 */
    private String receiptId;

    /** 执行耗时（毫秒） */
    private Long costMs;

    /** 节点执行轨迹 */
    private List<String> executionTrace;
}
