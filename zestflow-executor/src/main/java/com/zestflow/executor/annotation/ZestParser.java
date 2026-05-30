package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 结果解析器元件。
 * <p>
 * 负责将前驱执行器节点的执行结果解析到 ChainContext 中，
 * 供下游节点使用。通常挂在执行器节点之后，作为后处理步骤。
 * <p>
 * 方法签名：方法可以接收 ChainContext 以及通过 @ZestResult 注解
 * 注入的前驱节点执行结果。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestComponent("order")
 * public class OrderHandler {
 *     @ZestParser("parseOrderResult")
 *     public void parse(ChainContext ctx, @ZestResult Object result) {
 *         ctx.put("orderId", result.getOrderId());
 *         ctx.put("totalAmount", result.getAmount());
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestParser {

    /**
     * 解析器唯一标识。
     * 为空时默认取 "类简单名.方法名"
     */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
