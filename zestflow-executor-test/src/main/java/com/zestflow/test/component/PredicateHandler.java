package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestPredicate;
import com.zestflow.executor.annotation.ZestTag;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("predicate")
public class PredicateHandler {

    @ZestPredicate(value = "predStockAvailable", name = "库存检查")
    @ZestTag(name="有货", value="true")
    @ZestTag(name="缺货", value="false")
    public boolean predStockAvailable(ChainContext ctx) {
        return true; }

    @ZestPredicate(value = "predUserVip", name = "VIP判断")
    @ZestTag(name="VIP用户", value="true")
    @ZestTag(name="普通用户", value="false")
    public boolean predUserVip(ChainContext ctx) {
        return false; }

    @ZestPredicate(value = "predOrderPaid", name = "支付状态判断")
    @ZestTag(name="已支付", value="true")
    @ZestTag(name="未支付", value="false")
    public boolean predOrderPaid(ChainContext ctx) {
        return true; }

    @ZestPredicate(value = "predRiskPass", name = "风控判断")
    @ZestTag(name="通过", value="true")
    @ZestTag(name="拒绝", value="false")
    public boolean predRiskPass(ChainContext ctx) {
        return true; }

    @ZestPredicate(value = "predTimeWindow", name = "时间窗口判断")
    public boolean predTimeWindow(ChainContext ctx) {
        return false; }

    @ZestPredicate(value = "predInventoryEnough", name = "库存充足判断")
    public boolean predInventoryEnough(ChainContext ctx) {
        return true; }

    @ZestPredicate(value = "predCouponValid", name = "优惠券有效性")
    public boolean predCouponValid(ChainContext ctx) {
        return true; }

    @ZestPredicate(value = "predAddressValid", name = "地址有效性")
    public boolean predAddressValid(ChainContext ctx) {
        return false; }

    @ZestPredicate(value = "predPaymentMethod", name = "支付方式判断")
    public boolean predPaymentMethod(ChainContext ctx) {
        return true; }

    @ZestPredicate(value = "predNeedAudit", name = "是否需要审核")
    public boolean predNeedAudit(ChainContext ctx) {
        return false; }
}
