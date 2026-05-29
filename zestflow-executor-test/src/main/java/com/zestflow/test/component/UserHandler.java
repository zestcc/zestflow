package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("user")
public class UserHandler {

    @ZestExecute(value = "validateUser", description = "校验用户合法性")
    public Map<String, Object> validateUser(ChainContext ctx) {
        String userId = ctx.get("userId", String.class);
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        log.info("用户校验通过 userId={}", userId);
        ctx.put("validated", true);
        return Map.of("userId", userId, "valid", true);
    }

    @ZestExecute(value = "sendNotify", description = "发送通知")
    public Map<String, Object> sendNotify(ChainContext ctx) {
        String userId = ctx.get("userId", String.class);
        Object orderId = ctx.get("orderId");
        log.info("发送通知 userId={} orderId={}", userId, orderId);
        return Map.of("sent", true, "channel", "SMS");
    }
}
