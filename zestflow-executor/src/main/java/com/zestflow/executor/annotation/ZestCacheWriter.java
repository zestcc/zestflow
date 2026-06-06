package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为缓存写入器元件。
 * <p>
 * 将 DataBus 数据写入缓存（Redis / Caffeine 等）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestCacheWriter {

    /** 缓存写入器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 缓存过期时间（秒），-1 表示永不过期 */
    long ttlSeconds() default 3600;
}