package com.zestflow.executor.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明应用侧链契约 — 启动时自动在 DB 占位（status=未设计），运行时通过 {@code chain_key} 解析编码。
 * <p>
 * 可标注在 Controller 方法或类上；类级注解对该类所有 public 映射方法生效（需配合 {@code @RequestMapping}）。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ZestChain {

    /** 稳定链标识，如 heytrip.ota.getHotels */
    String value();

    /** 展示名称，占位创建时使用 */
    String name() default "";

    /** 描述 */
    String description() default "";
}
