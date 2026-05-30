package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 后置处理器元件。
 * <p>
 * 在节点主逻辑（@ZestExecute）执行之后运行，
 * 用于结果增强、数据脱敏、日志记录、通知发送等后处理操作。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestPostProcessor {

    /**
     * 后置处理器唯一标识。
     * 为空时默认取 "类简单名.方法名"
     */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
