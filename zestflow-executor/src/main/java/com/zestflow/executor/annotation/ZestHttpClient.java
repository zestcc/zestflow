package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 HTTP 调用器元件。
 * <p>
 * 内置 HTTP 远程调用能力，支持模板化 URL、Header 注入和响应解析。
 * 可作为独立组件，也可通过内置组件库自动注册。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestHttpClient {

    /** HTTP 调用器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** HTTP 方法：GET / POST / PUT / DELETE */
    String method() default "GET";

    /** 请求 URL（支持 {key} 占位符） */
    String url() default "";
}