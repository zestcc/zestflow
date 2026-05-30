package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 数据加载器元件。
 * <p>
 * 负责从外部数据源（数据库、缓存、HTTP 接口等）获取数据，
 * 并将数据写入 ChainContext，供后续节点消费。
 * 典型的用法是在执行器之前加载必要的上下文数据。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestComponent("order")
 * public class OrderHandler {
 *     @ZestLoader("loadPriceConfig")
 *     public void loadConfig(ChainContext ctx) {
 *         PriceConfig config = priceService.getConfig();
 *         ctx.put("priceConfig", config);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestLoader {

    /**
     * 加载器唯一标识。
     * 为空时默认取 "类简单名.方法名"
     */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
