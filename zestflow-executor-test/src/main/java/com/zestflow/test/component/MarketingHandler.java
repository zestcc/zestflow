package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("marketing")
public class MarketingHandler {

    @ZestExecute(value = "createCampaign", name = "创建营销活动")
    public Map<String, Object> createCampaign(ChainContext ctx) {
        log.info("营销-创建活动");
        return Map.of("campaignId", "CAM" + System.currentTimeMillis(), "type", "FULL_REDUCE");
    }

    @ZestExecute(value = "issueCoupon", name = "发放优惠券")
    public Map<String, Object> issueCoupon(ChainContext ctx) {
        log.info("营销-发放优惠券");
        return Map.of("couponId", "CPN" + System.currentTimeMillis(), "value", 20.0);
    }

    @ZestExecute(value = "calcDiscount", name = "计算折扣")
    public Map<String, Object> calcDiscount(ChainContext ctx) {
        log.info("营销-计算折扣");
        return Map.of("originalPrice", 200.0, "discountPrice", 160.0);
    }

    @ZestExecute(value = "applyPromotion", name = "应用促销")
    public Map<String, Object> applyPromotion(ChainContext ctx) {
        log.info("营销-应用促销");
        return Map.of("promotionType", "SECONDS_HALF", "reducedAmount", 50.0);
    }

    @ZestExecute(value = "sendPushPromotion", name = "推送促销消息")
    public Map<String, Object> sendPushPromotion(ChainContext ctx) {
        log.info("营销-推送促销");
        return Map.of("sentCount", 10000, "channel", "PUSH");
    }

    @ZestExecute(value = "redeemPoints", name = "积分兑换")
    public Map<String, Object> redeemPoints(ChainContext ctx) {
        log.info("营销-积分兑换");
        return Map.of("pointsUsed", 1000, "reward", "满100减10券");
    }

    @ZestExecute(value = "checkUserTag", name = "用户标签匹配")
    public Map<String, Object> checkUserTag(ChainContext ctx) {
        log.info("营销-用户标签匹配");
        return Map.of("matched", true, "tags", new String[]{"高消费", "VIP"});
    }

    @ZestExecute(value = "calcCashback", name = "计算返现")
    public Map<String, Object> calcCashback(ChainContext ctx) {
        log.info("营销-计算返现");
        return Map.of("cashbackAmount", 15.0, "rule", "ORDER_AMOUNT_5%");
    }

    @ZestExecute(value = "groupBuyProcess", name = "拼团处理")
    public Map<String, Object> groupBuyProcess(ChainContext ctx) {
        log.info("营销-拼团处理");
        return Map.of("groupId", "GRP" + System.currentTimeMillis(), "members", 3);
    }

    @ZestExecute(value = "seckillValidate", name = "秒杀校验")
    public Map<String, Object> seckillValidate(ChainContext ctx) {
        log.info("营销-秒杀校验");
        return Map.of("available", true, "stock", 50);
    }
}
