package com.zestflow.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotBlank(message = "订单ID不能为空")
    private String orderId;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @Min(value = 1, message = "金额必须大于0")
    private int amount;

    /** 支付方式 */
    private String method;

    /** 审批金额（条件分支用） */
    private Integer approvalAmount;

    /** 是否触发重试 */
    @Builder.Default
    private boolean triggerRetry = false;

    /** 是否触发熔断 */
    @Builder.Default
    private boolean triggerCircuitBreak = false;

    /** 模拟失败次数 */
    @Builder.Default
    private int simulateFailCount = 0;
}
