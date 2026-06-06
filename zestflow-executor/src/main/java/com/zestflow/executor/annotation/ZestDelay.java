package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为延迟器元件。
 * <p>
 * 用于流程中的定时等待/轮询间隔控制。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestDelay {

    /** 延迟器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 延迟时间（毫秒） */
    long delayMs() default 1000;
}