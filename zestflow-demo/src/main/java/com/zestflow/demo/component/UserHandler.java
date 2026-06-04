package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestLoader;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.demo.component.model.user.UserResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("user")
public class UserHandler {

    @ZestLoader(value = "loadUserInfo", name = "加载用户信息")
    public void loadUserInfo(ChainContext ctx) {
        if (ctx.get("userId") == null) {
            ctx.put("userId", "U-LOADER");
        }
        ctx.put("userName", "Demo User");
        log.info("已加载用户信息 userId={}", ctx.get("userId"));
    }

    @ZestExecute(value = "validateUser", description = "校验用户合法性")
    public UserResults.ValidateUserResult validateUser(@ZestParam(value = "userId", required = true) String userId) {
        log.info("用户校验通过 userId={}", userId);
        return new UserResults.ValidateUserResult(userId, true);
    }

    @ZestExecute(value = "sendNotify", description = "发送通知")
    public UserResults.SendNotifyResult sendNotify(
            @ZestParam("userId") String userId,
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("发送通知 userId={} orderId={}", userId, orderId);
        return new UserResults.SendNotifyResult(true, "SMS", userId != null ? userId : "");
    }
}
