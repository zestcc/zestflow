package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 数据加载器元件。
 * <p>
 * 负责从外部数据源获取数据并写入 ChainContext，供后续节点消费。
 * <p>
 * 写入方式（三选一）：
 * <ul>
 *   <li>方法内 {@code ctx.put(key, value)}（void 返回）</li>
 *   <li>返回 {@code Map} / POJO（自动展平到 DataBus）</li>
 *   <li>返回简单类型 + {@link ZestOutput} 指定 key</li>
 * </ul>
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
