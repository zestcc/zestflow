package com.zestflow.test.component;

import com.zestflow.executor.annotation.*;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("order")
public class OrderHandler {

    @ZestExecute(value = "createOrder", name = "创建订单")
    public Map<String, Object> createOrder(ChainContext ctx) {
        log.info("创建订单 params={}", ctx.snapshot());
        ctx.put("orderId", "ORD-" + System.currentTimeMillis());
        ctx.put("amount", 99.9);
        return Map.of("orderId", ctx.get("orderId"), "amount", 99.9);
    }

    @ZestExecute(value = "findAfterSale", name = "查找售后单")
    public void findAfterSale(ChainContext ctx) {
        log.info("执行查找售后单");
    }

    @ZestPredicate(value = "auditAfterSale", name = "售后审核")
    @ZestTag(name = "同意", value = "true")
    @ZestTag(name = "不同意", value = "false")
    public boolean auditAfterSale(ChainContext ctx) {
        log.info("执行售后审核");
        return true;
    }

    @ZestSelector(value = "handleAfterSale", name = "售后处理")
    @ZestTag(name = "退货", value = "return")
    @ZestTag(name = "换货", value = "exchange")
    public String handleAfterSale(ChainContext ctx) {
        log.info("执行售后处理");
        return "return";
    }

    @ZestExecute(value = "processReturnGoodsInStock", name = "退货")
    public void processReturnGoodsInStock(ChainContext ctx) {
        log.info("执行退货");
    }

    @ZestExecute(value = "processExchangeGenerateOrder", name = "换货")
    public void processExchangeGenerateOrder(ChainContext ctx) {
        log.info("执行换货");
    }

    @ZestExecute(value = "refundOrder", name = "退款处理")
    public Map<String, Object> refundOrder(ChainContext ctx) {
        String orderId = ctx.get("orderId", String.class);
        Object amount = ctx.get("amount");
        log.info("退款 orderId={} amount={}", orderId, amount);
        return Map.of("orderId", orderId, "refundAmount", amount, "status", "REFUNDED");
    }

    @ZestExecute(value = "shipExchangeOrder", name = "发货")
    public void shipExchangeOrder(ChainContext ctx) {
        log.info("执行发货");
    }

    @ZestExecute(value = "createOrder", name = "创建订单")
    public String auditOrder(ChainContext ctx) {
        return "";
    }

    @ZestExecute(value = "cancelOrder", name = "取消订单", timeout = 3000)
    public Map<String, Object> cancelOrder(ChainContext ctx) {
        String orderId = ctx.get("orderId", String.class);
        log.info("取消订单 orderId={}", orderId);
        return Map.of("orderId", orderId, "status", "CANCELLED");
    }
}
