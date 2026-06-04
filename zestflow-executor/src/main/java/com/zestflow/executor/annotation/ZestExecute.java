package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 可编排的执行元件。
 * <p>
 * value() 作为全局唯一标识，在链定义的 graph_data.nodes[].component 中引用。
 * 默认值为 "类简单名.方法名"，建议显式指定简短 ID。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestComponent("order")
 * public class OrderHandler {
 *     @ZestExecute("createOrder")
 *     public Result create(ChainContext ctx) { ... }
 *
 *     @ZestExecute("cancelOrder")
 *     public Result cancel(ChainContext ctx) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestExecute {

    /**
     * 执行元件唯一标识，对应链定义中 node.component 的值。
     * 为空时默认取方法名
     */
    String value() default "";

    /**
     * 显示名称（可选），用于 Admin 页面展示
     */
    String name() default "";

    /**
     * 描述（可选）
     */
    String description() default "";

    /**
     * 超时时间（毫秒）
     * -1 使用节点配置中的 timeout
     * 0 表示无限等待
     */
    long timeout() default -1;

    /** 是否异步执行（不等待结果） */
    boolean async() default false;
}
