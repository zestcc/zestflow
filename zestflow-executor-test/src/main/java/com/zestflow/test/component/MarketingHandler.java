package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.annotation.ZestSelector;
import com.zestflow.executor.annotation.ZestTag;
import com.zestflow.test.component.model.marketing.MarketingResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("marketing")
public class MarketingHandler {

    @ZestExecute(value = "createCampaign", name = "创建营销活动")
    public MarketingResults.CreateCampaignResult createCampaign() {
        log.info("营销-创建活动");
        return new MarketingResults.CreateCampaignResult("CAM" + System.currentTimeMillis(), "FULL_REDUCE");
    }

    @ZestExecute(value = "issueCoupon", name = "发放优惠券")
    public MarketingResults.IssueCouponResult issueCoupon() {
        log.info("营销-发放优惠券");
        return new MarketingResults.IssueCouponResult("CPN" + System.currentTimeMillis(), 20.0);
    }

    @ZestExecute(value = "calcDiscount", name = "计算折扣")
    public MarketingResults.CalcDiscountResult calcDiscount(
            @ZestParam(value = "amount", defaultValue = "200") double amount) {
        log.info("营销-计算折扣 amount={}", amount);
        return new MarketingResults.CalcDiscountResult(amount, amount * 0.8);
    }

    @ZestExecute(value = "applyPromotion", name = "应用促销")
    public MarketingResults.ApplyPromotionResult applyPromotion() {
        log.info("营销-应用促销");
        return new MarketingResults.ApplyPromotionResult("SECONDS_HALF", 50.0);
    }

    @ZestExecute(value = "sendPushPromotion", name = "推送促销消息")
    public MarketingResults.SendPushPromotionResult sendPushPromotion() {
        log.info("营销-推送促销");
        return new MarketingResults.SendPushPromotionResult(10000, "PUSH");
    }

    @ZestExecute(value = "redeemPoints", name = "积分兑换")
    public MarketingResults.PointsRedeemResult redeemPoints(MarketingResults.CashbackResult cashback) {
        int points = (int) (cashback.cashbackAmount() * 100);
        log.info("营销-积分入账 cashback={} points={}", cashback.cashbackAmount(), points);
        return new MarketingResults.PointsRedeemResult(points, "满100减10券");
    }

    @ZestExecute(value = "checkUserTag", name = "用户标签匹配")
    public MarketingResults.CheckUserTagResult checkUserTag(
            @ZestParam(value = "userId", required = false) String userId) {
        log.info("营销-用户标签匹配 userId={}", userId);
        return new MarketingResults.CheckUserTagResult(true, new String[]{"高消费", "VIP"});
    }

    @ZestExecute(value = "calcCashback", name = "计算返现")
    public MarketingResults.CashbackResult calcCashback(
            @ZestParam(value = "amount", defaultValue = "300") double amount) {
        double cashback = Math.round(amount * 0.05 * 100.0) / 100.0;
        log.info("营销-计算返现 amount={} cashback={}", amount, cashback);
        return new MarketingResults.CashbackResult(cashback, "ORDER_AMOUNT_5%");
    }

    @ZestExecute(value = "groupBuyProcess", name = "拼团处理")
    public MarketingResults.GroupBuyProcessResult groupBuyProcess() {
        log.info("营销-拼团处理");
        return new MarketingResults.GroupBuyProcessResult("GRP" + System.currentTimeMillis(), 3);
    }

    @ZestExecute(value = "seckillValidate", name = "秒杀校验")
    public MarketingResults.SeckillValidateResult seckillValidate(
            @ZestParam(value = "productId", required = false) String productId) {
        log.info("营销-秒杀校验 productId={}", productId);
        return new MarketingResults.SeckillValidateResult(true, 50);
    }

    @ZestSelector(value = "routePromotion", name = "促销路由")
    @ZestTag(name = "发券", value = "coupon")
    @ZestTag(name = "返现", value = "cashback")
    public String routePromotion() {
        log.info("营销-促销路由");
        return "coupon";
    }
}
