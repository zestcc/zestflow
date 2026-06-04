package com.zestflow.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "商品ID不能为空")
    private String productId;

    @Min(value = 1, message = "数量至少为1")
    private int quantity;

    @Min(value = 0, message = "金额不能为负")
    private int amount;

    /** 支付方式：WECHAT/ALIPAY/CARD */
    private String payMethod;

    /** 渠道来源：APP/WEB/H5 */
    private String channel;

    /** 订单项列表（批量场景） */
    @Valid
    private List<OrderItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItem {
        @NotBlank
        private String sku;
        @Min(1)
        private int qty;
        @Min(0)
        private int price;
    }
}
