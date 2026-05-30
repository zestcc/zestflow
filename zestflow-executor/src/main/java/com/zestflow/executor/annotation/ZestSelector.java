package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 选择器元件。
 * <p>
 * 方法必须返回 String 类型（路由标识），用于多条件节点的分支选择。
 * 返回值对应边的 condition 标签，执行引擎根据返回值路由到对应的下游分支。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestComponent("order")
 * public class OrderHandler {
 *     @ZestSelector("routeByChannel")
 *     public String route(ChainContext ctx) {
 *         String channel = ctx.get("channel", String.class);
 *         return "online".equals(channel) ? "online" : "offline";
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestSelector {

    /**
     * 选择器唯一标识。
     * 为空时默认取 "类简单名.方法名"
     */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
