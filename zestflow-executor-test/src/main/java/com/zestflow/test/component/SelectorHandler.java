package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestSelector;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("selector")
public class SelectorHandler {

    @ZestSelector(value = "selPaymentRoute", name = "支付路由选择")
    public String selPaymentRoute(ChainContext ctx) {
        return "wechat"; }

    @ZestSelector(value = "selExpressCompany", name = "快递公司选择")
    public String selExpressCompany(ChainContext ctx) {
        return "sf"; }

    @ZestSelector(value = "selDiscountType", name = "优惠类型选择")
    public String selDiscountType(ChainContext ctx) {
        return "percentage"; }

    @ZestSelector(value = "selNotifyChannel", name = "通知渠道选择")
    public String selNotifyChannel(ChainContext ctx) {
        return "sms"; }

    @ZestSelector(value = "selWarehouse", name = "仓库选择")
    public String selWarehouse(ChainContext ctx) {
        return "east"; }

    @ZestSelector(value = "selAuditLevel", name = "审核等级选择")
    public String selAuditLevel(ChainContext ctx) {
        return "high"; }

    @ZestSelector(value = "selDeliveryType", name = "配送方式选择")
    public String selDeliveryType(ChainContext ctx) {
        return "express"; }

    @ZestSelector(value = "selCouponType", name = "优惠券类型选择")
    public String selCouponType(ChainContext ctx) {
        return "full_reduce"; }

    @ZestSelector(value = "selSettlementPeriod", name = "结算周期选择")
    public String selSettlementPeriod(ChainContext ctx) {
        return "monthly"; }

    @ZestSelector(value = "selTaxCalcMethod", name = "计税方式选择")
    public String selTaxCalcMethod(ChainContext ctx) {
        return "standard"; }
}
