package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestLoader;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("loader")
public class LoaderHandler {

    @ZestLoader(value = "loadUserInfo", name = "用户信息加载")
    public void loadUserInfo(ChainContext ctx) {
        ctx.put("userName", "张三"); }

    @ZestLoader(value = "loadProductPrice", name = "商品价格加载")
    public void loadProductPrice(ChainContext ctx) {
        ctx.put("price", 199.9); }

    @ZestLoader(value = "loadCouponList", name = "优惠券列表加载")
    public void loadCouponList(ChainContext ctx) {
        ctx.put("coupons", new String[]{"满100减20", "满200减50"}); }

    @ZestLoader(value = "loadAddress", name = "地址信息加载")
    public void loadAddress(ChainContext ctx) {
        ctx.put("address", "北京市朝阳区"); }

    @ZestLoader(value = "loadInventory", name = "库存数量加载")
    public void loadInventory(ChainContext ctx) {
        ctx.put("stock", 100); }

    @ZestLoader(value = "loadConfigRules", name = "规则配置加载")
    public void loadConfigRules(ChainContext ctx) {
        ctx.put("maxDiscount", 0.3); }

    @ZestLoader(value = "loadRiskProfile", name = "风险画像加载")
    public void loadRiskProfile(ChainContext ctx) {
        ctx.put("riskLevel", "low"); }

    @ZestLoader(value = "loadVipDiscount", name = "VIP折扣加载")
    public void loadVipDiscount(ChainContext ctx) {
        ctx.put("vipDiscount", 0.95); }

    @ZestLoader(value = "loadShippingFee", name = "运费加载")
    public void loadShippingFee(ChainContext ctx) {
        ctx.put("shippingFee", 10.0); }

    @ZestLoader(value = "loadTaxRate", name = "税率加载")
    public void loadTaxRate(ChainContext ctx) {
        ctx.put("taxRate", 0.13); }
}
