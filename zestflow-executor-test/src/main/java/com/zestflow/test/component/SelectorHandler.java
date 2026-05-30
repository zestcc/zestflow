package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestSelector;
import com.zestflow.executor.annotation.ZestTag;
import com.zestflow.executor.annotation.ZestTags;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("selector")
public class SelectorHandler {

    @ZestSelector(value = "selPaymentRoute", name = "支付路由选择")
    @ZestTags({
        @ZestTag(name="微信", value="wechat"),
        @ZestTag(name="支付宝", value="alipay"),
        @ZestTag(name="银行卡", value="card")
    })
    public String selPaymentRoute(ChainContext ctx) {
        return "wechat"; }

    @ZestSelector(value = "selExpressCompany", name = "快递公司选择")
    @ZestTags({
        @ZestTag(name="顺丰", value="sf"),
        @ZestTag(name="圆通", value="yuantong"),
        @ZestTag(name="中通", value="zhongtong")
    })
    public String selExpressCompany(ChainContext ctx) {
        return "sf"; }

    @ZestSelector(value = "selDiscountType", name = "优惠类型选择")
    @ZestTags({
        @ZestTag(name="百分比", value="percentage"),
        @ZestTag(name="固定金额", value="fixed")
    })
    public String selDiscountType(ChainContext ctx) {
        return "percentage"; }

    @ZestSelector(value = "selNotifyChannel", name = "通知渠道选择")
    @ZestTags({
        @ZestTag(name="短信", value="sms"),
        @ZestTag(name="邮件", value="email"),
        @ZestTag(name="站内信", value="internal")
    })
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
