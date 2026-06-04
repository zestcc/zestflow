package com.zestflow.demo.component.model.marketing;

/** 营销域元件返回值。 */
public final class MarketingResults {
    private MarketingResults() {}

    public record CreateCampaignResult(String campaignId, String type) {}
    public record IssueCouponResult(String couponId, double value) {}
    public record CalcDiscountResult(double originalPrice, double discountPrice) {}
    public record ApplyPromotionResult(String promotionType, double reducedAmount) {}
    public record SendPushPromotionResult(int sentCount, String channel) {}
    public record CheckUserTagResult(boolean matched, String[] tags) {}
    public record CashbackResult(double cashbackAmount, String rule) {}
    public record PointsRedeemResult(int pointsUsed, String reward) {}
    public record GroupBuyProcessResult(String groupId, int members) {}
    public record SeckillValidateResult(boolean available, int stock) {}
}
