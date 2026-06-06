package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为日志记录器元件。
 * <p>
 * 独立日志埋点组件，用于在流程中显式记录关键节点日志，
 * 与全链路自动事件采集互补。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestLogger {

    /** 日志记录器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 日志级别：DEBUG / INFO / WARN / ERROR */
    String level() default "INFO";
}