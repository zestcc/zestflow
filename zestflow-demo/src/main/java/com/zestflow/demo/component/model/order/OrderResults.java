package com.zestflow.demo.component.model.order;

/** 订单域元件返回值。 */
public final class OrderResults {
    private OrderResults() {}

    public record OrderCreatedResult(String orderId, double amount) {}
    public record AfterSaleFoundResult(String applyId, String orderId, double amount) {}
    public record RefundOrderResult(String orderId, Object refundAmount, String status) {}
    public record CancelOrderResult(String orderId, String status) {}
    public record ReturnGoodsResult(boolean done) {}
    public record ExchangeOrderResult(boolean done) {}
    public record ShipExchangeResult(boolean done) {}
}
