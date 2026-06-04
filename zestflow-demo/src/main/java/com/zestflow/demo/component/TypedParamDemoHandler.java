package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.demo.component.model.demo.DemoResults;
import lombok.extern.slf4j.Slf4j;

/**
 * 强类型参数演示元件 — 业务方法不依赖 ChainContext，仅通过 @ZestParam / 类型化返回值。
 */
@Slf4j
@ZestComponent("typed")
public class TypedParamDemoHandler {

    @ZestExecute(value = "echoUser", name = "回显用户")
    public DemoResults.EchoUserResult echoUser(@ZestParam(value = "userId", required = true) String userId) {
        log.info("echoUser userId={}", userId);
        return new DemoResults.EchoUserResult(userId, true);
    }

    @ZestExecute(value = "scaleAmount", name = "金额放大")
    public DemoResults.ScaleAmountResult scaleAmount(@ZestParam("amount") int amount) {
        return new DemoResults.ScaleAmountResult(amount * 2);
    }

    @ZestExecute(value = "greetName", name = "问候")
    public DemoResults.GreetNameResult greetName(@ZestParam(value = "name", defaultValue = "guest") String name) {
        return new DemoResults.GreetNameResult("hello," + name);
    }

    @ZestExecute(value = "consumeScaled", name = "消费放大结果")
    public DemoResults.ConsumeScaledResult consumeScaled(@ZestParam("scaledAmount") int scaled) {
        return new DemoResults.ConsumeScaledResult(scaled);
    }

    @ZestExecute(value = "readBoundOrder", name = "读取绑定后订单参数")
    public DemoResults.ReadBoundOrderResult readBoundOrder(
            @ZestParam("orderId") String orderId,
            @ZestParam("userId") String userId) {
        return new DemoResults.ReadBoundOrderResult(orderId, userId);
    }
}
