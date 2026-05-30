package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestParamBinder;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("parambinder")
public class ParamBinderHandler {

    @ZestParamBinder(value = "bindOrderParam", name = "订单参数绑定")
    public void bindOrderParam(ChainContext ctx) {
        Object rawOrderId = ctx.get("rawOrderId");
        ctx.put("orderId", rawOrderId != null ? rawOrderId.toString() : "");
        Object rawAmount = ctx.get("rawAmount");
        ctx.put("amount", Double.parseDouble(rawAmount != null ? rawAmount.toString() : "0"));
    }

    @ZestParamBinder(value = "bindUserParam", name = "用户参数绑定")
    public void bindUserParam(ChainContext ctx) {
        Object rawUserId = ctx.get("rawUserId");
        ctx.put("userId", Long.parseLong(rawUserId != null ? rawUserId.toString() : "0"));
    }

    @ZestParamBinder(value = "bindPayParam", name = "支付参数绑定")
    public void bindPayParam(ChainContext ctx) {
        Object rawPayAmount = ctx.get("rawPayAmount");
        ctx.put("payAmount", Double.parseDouble(rawPayAmount != null ? rawPayAmount.toString() : "0"));
    }

    @ZestParamBinder(value = "bindSearchParam", name = "搜索参数绑定")
    public void bindSearchParam(ChainContext ctx) {
        Object rawKeyword = ctx.get("rawKeyword");
        ctx.put("keyword", rawKeyword != null ? rawKeyword.toString() : "");
        Object rawPageSize = ctx.get("rawPageSize");
        ctx.put("pageSize", Integer.parseInt(rawPageSize != null ? rawPageSize.toString() : "10"));
    }
}
