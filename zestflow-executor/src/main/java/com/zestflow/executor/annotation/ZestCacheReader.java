package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为缓存读取器元件。
 * <p>
 * 从缓存（Redis / Caffeine 等）读取数据并写入 DataBus。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestCacheReader {

    /** 缓存读取器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 缓存 key 模板（支持 {param} 占位符） */
    String cacheKey() default "";
}