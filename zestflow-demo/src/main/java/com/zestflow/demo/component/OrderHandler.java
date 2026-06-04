package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParser;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.annotation.ZestParamBinder;
import com.zestflow.executor.annotation.ZestPostProcessor;
import com.zestflow.executor.annotation.ZestPreProcessor;
import com.zestflow.executor.annotation.ZestPredicate;
import com.zestflow.executor.annotation.ZestSelector;
import com.zestflow.executor.annotation.ZestTag;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.demo.component.model.order.OrderResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("order")
public class OrderHandler {

    @ZestExecute(value = "createOrder", name = "创建订单")
    public OrderResults.OrderCreatedResult createOrder(
            @ZestParam(value = "userId", defaultValue = "U001") String userId,
            @ZestParam(value = "productId", defaultValue = "PROD-DEMO") String productId,
            @ZestParam(value = "quantity", defaultValue = "1") int quantity,
            @ZestParam(value = "amount", defaultValue = "99.9") double amount) {
        String orderId = "ORD-" + System.currentTimeMillis();
        log.info("创建订单 userId={} productId={} quantity={} orderId={}", userId, productId, quantity, orderId);
        return new OrderResults.OrderCreatedResult(orderId, amount);
    }

    @ZestExecute(value = "findAfterSale", name = "查找售后单")
    public OrderResults.AfterSaleFoundResult findAfterSale(
            @ZestParam(value = "applyId", required = false) String applyId,
            @ZestParam(value = "orderId", required = false) String orderId) {
        if (applyId == null || applyId.isBlank()) {
            applyId = (orderId != null && !orderId.isBlank()) ? orderId : "DEMO-APPLY-001";
        }
        String resolvedOrderId = "ORD-" + applyId;
        double amount = 179.0;
        log.info("查找售后单 applyId={} orderId={} amount={}", applyId, resolvedOrderId, amount);
        return new OrderResults.AfterSaleFoundResult(applyId, resolvedOrderId, amount);
    }

    @ZestPredicate(value = "auditAfterSale", name = "售后审核")
    @ZestTag(name = "同意", value = "true")
    @ZestTag(name = "不同意", value = "false")
    public boolean auditAfterSale(@ZestParam(value = "applyId", required = false) String applyId) {
        log.info("执行售后审核 applyId={}", applyId);
        return true;
    }

    @ZestSelector(value = "handleAfterSale", name = "售后处理")
    @ZestTag(name = "退货", value = "return")
    @ZestTag(name = "换货", value = "exchange")
    public String handleAfterSale(@ZestParam(value = "applyId", required = false) String applyId) {
        log.info("执行售后处理 applyId={}", applyId);
        return "return";
    }

    @ZestExecute(value = "processReturnGoodsInStock", name = "退货")
    public OrderResults.ReturnGoodsResult processReturnGoodsInStock(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("执行退货 orderId={}", orderId);
        return new OrderResults.ReturnGoodsResult(true);
    }

    @ZestExecute(value = "processExchangeGenerateOrder", name = "换货")
    public OrderResults.ExchangeOrderResult processExchangeGenerateOrder(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("执行换货 orderId={}", orderId);
        return new OrderResults.ExchangeOrderResult(true);
    }

    @ZestExecute(value = "refundOrder", name = "退款处理")
    public OrderResults.RefundOrderResult refundOrder(
            @ZestParam(value = "orderId", defaultValue = "ORD-DEMO-REFUND") String orderId,
            @ZestParam(value = "amount", defaultValue = "179") double amount) {
        log.info("退款 orderId={} amount={}", orderId, amount);
        return new OrderResults.RefundOrderResult(orderId, amount, "REFUNDED");
    }

    @ZestExecute(value = "shipExchangeOrder", name = "发货")
    public OrderResults.ShipExchangeResult shipExchangeOrder(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("执行发货 orderId={}", orderId);
        return new OrderResults.ShipExchangeResult(true);
    }

    @ZestPreProcessor(value = "preCheckOrder", name = "订单前置检查")
    public void preCheckOrder(ChainContext ctx) {
        log.debug("订单前置检查 userId={}", ctx.get("userId"));
    }

    @ZestPostProcessor(value = "postOrderAudit", name = "订单后置审计")
    public void postOrderAudit(ChainContext ctx) {
        log.debug("订单后置审计 orderId={}", ctx.get("orderId"));
    }

    @ZestParamBinder(value = "bindOrderParam", name = "绑定订单参数")
    public void bindOrderParam(ChainContext ctx) {
        Object rawOrderId = ctx.get("rawOrderId");
        if (rawOrderId != null) {
            ctx.put("orderId", rawOrderId.toString());
        }
        Object rawAmount = ctx.get("rawAmount");
        if (rawAmount != null) {
            ctx.put("amount", Double.parseDouble(rawAmount.toString()));
        }
        if (ctx.get("userId") == null) {
            ctx.put("userId", "U-BIND");
        }
    }

    @ZestParser(value = "parseOrderResult", name = "解析订单结果")
    public void parseOrderResult(ChainContext ctx) {
        Object orderId = ctx.get("orderId");
        if (orderId != null) {
            ctx.put("parsedOrderId", orderId.toString());
        }
    }

    @ZestExecute(value = "cancelOrder", name = "取消订单", timeout = 3000)
    public OrderResults.CancelOrderResult cancelOrder(
            @ZestParam(value = "orderId", defaultValue = "ORD-DEMO-CANCEL") String orderId) {
        log.info("取消订单 orderId={}", orderId);
        return new OrderResults.CancelOrderResult(orderId, "CANCELLED");
    }
}
