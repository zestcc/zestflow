package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 判断器元件。
 * <p>
 * 方法必须返回 boolean 类型，用于条件节点的 true/false 路由决策。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestComponent("order")
 * public class OrderHandler {
 *     @ZestPredicate("stockAvailable")
 *     public boolean checkStock(ChainContext ctx) {
 *         return ctx.get("stock", Integer.class) > 0;
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestPredicate {

    /**
     * 判断器唯一标识，对应链定义中 condition 节点的引用。
     * 为空时默认取 "类简单名.方法名"
     */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
