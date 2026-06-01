package com.zestflow.test.playground.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付流转请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFlowRequest {

    @NotBlank(message = "订单号不能为空")
    @Size(max = 64, message = "订单号长度不能超过64")
    private String orderId;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "金额不能小于0.01")
    @DecimalMax(value = "9999999.99", message = "金额不能超过9999999.99")
    private BigDecimal amount;

    @NotBlank(message = "币种不能为空")
    @Pattern(regexp = "^(USD|EUR|CNY|GBP|JPY|HKD|SGD)$", message = "不支持的币种")
    private String currency;

    @NotBlank(message = "付款账号不能为空")
    @Size(max = 32, message = "付款账号长度不能超过32")
    private String payerAccount;

    @NotBlank(message = "收款账号不能为空")
    @Size(max = 32, message = "收款账号长度不能超过32")
    private String payeeAccount;

    @Size(max = 256, message = "描述长度不能超过256")
    private String description;

    @Pattern(regexp = "^(BANK_TRANSFER|CREDIT_CARD|BALANCE)$", message = "不支持的支付方式")
    private String paymentMethod;
}
