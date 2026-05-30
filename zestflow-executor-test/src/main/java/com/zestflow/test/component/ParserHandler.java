package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestParser;
import com.zestflow.executor.annotation.ZestResult;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("parser")
public class ParserHandler {

    @ZestParser(value = "parseOrderResult", name = "订单结果解析")
    public void parseOrderResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("parsedOrderId", "ORD-001"); }

    @ZestParser(value = "parsePayResult", name = "支付结果解析")
    public void parsePayResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("payStatus", "success"); }

    @ZestParser(value = "parseShipResult", name = "发货结果解析")
    public void parseShipResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("trackingNo", "SF123456"); }

    @ZestParser(value = "parseUserData", name = "用户数据解析")
    public void parseUserData(ChainContext ctx, @ZestResult Object result) {
        ctx.put("userId", 10001); }

    @ZestParser(value = "parseRiskResult", name = "风控结果解析")
    public void parseRiskResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("riskScore", 60); }

    @ZestParser(value = "parsePriceCalc", name = "价格计算结果解析")
    public void parsePriceCalc(ChainContext ctx, @ZestResult Object result) {
        ctx.put("finalPrice", 99.0); }

    @ZestParser(value = "parseCouponResult", name = "优惠券结果解析")
    public void parseCouponResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("discountAmount", 20.0); }

    @ZestParser(value = "parseAuditResult", name = "审核结果解析")
    public void parseAuditResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("auditPassed", true); }

    @ZestParser(value = "parseNotifyResult", name = "通知结果解析")
    public void parseNotifyResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("notifySent", true); }

    @ZestParser(value = "parseLogResult", name = "日志结果解析")
    public void parseLogResult(ChainContext ctx, @ZestResult Object result) {
        ctx.put("logId", "LOG-" + System.currentTimeMillis()); }
}
