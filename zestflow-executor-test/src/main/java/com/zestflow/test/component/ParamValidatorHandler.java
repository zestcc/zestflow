package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestParamValidator;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;

@Slf4j
@ZestComponent("paramvalidator")
public class ParamValidatorHandler {

    private ChainContext extractContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof ChainContext ctx) {
                return ctx;
            }
        }
        return null;
    }

    @ZestParamValidator(value = "validateOrderParam", name = "订单参数校验")
    public void validateOrderParam(Object[] args, Parameter[] params) {
        ChainContext ctx = extractContext(args);
        if (ctx == null) {
            return;
        }
        Object orderId = ctx.get("orderId");
        if (orderId == null || orderId.toString().isEmpty()) {
            throw new IllegalArgumentException("订单ID不能为空");
        }
    }

    @ZestParamValidator(value = "validateUserParam", name = "用户参数校验")
    public void validateUserParam(Object[] args, Parameter[] params) {
        ChainContext ctx = extractContext(args);
        if (ctx == null) {
            return;
        }
        Object userId = ctx.get("userId");
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
    }

    @ZestParamValidator(value = "validatePayParam", name = "支付参数校验")
    public void validatePayParam(Object[] args, Parameter[] params) {
        ChainContext ctx = extractContext(args);
        if (ctx == null) {
            return;
        }
        Object amount = ctx.get("payAmount");
        if (amount == null || Double.parseDouble(amount.toString()) <= 0) {
            throw new IllegalArgumentException("支付金额必须大于0");
        }
    }

    @ZestParamValidator(value = "validateSearchParam", name = "搜索参数校验")
    public void validateSearchParam(Object[] args, Parameter[] params) {
        ChainContext ctx = extractContext(args);
        if (ctx == null) {
            return;
        }
        Object keyword = ctx.get("keyword");
        if (keyword == null || keyword.toString().isEmpty()) {
            throw new IllegalArgumentException("搜索关键词不能为空");
        }
    }
}
