package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("order")
public class OrderHandler {

    @ZestExecute(value = "createOrder", description = "创建订单")
    public Map<String, Object> createOrder(ChainContext ctx) {
        log.info("创建订单 params={}", ctx.snapshot());
        ctx.put("orderId", "ORD-" + System.currentTimeMillis());
        ctx.put("amount", 99.9);
        return Map.of("orderId", ctx.get("orderId"), "amount", 99.9);
    }

    @ZestExecute(value = "cancelOrder", description = "取消订单", timeout = 3000)
    public Map<String, Object> cancelOrder(ChainContext ctx) {
        String orderId = ctx.get("orderId", String.class);
        log.info("取消订单 orderId={}", orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }

    @ZestExecute(value = "refundOrder", description = "退款处理")
    public Map<String, Object> refundOrder(ChainContext ctx) {
        String orderId = ctx.get("orderId", String.class);
        Object amount = ctx.get("amount");
        log.info("退款 orderId={} amount={}", orderId, amount);
        return Map.of("orderId", orderId, "refundAmount", amount, "status", "REFUNDED");
    }
}
